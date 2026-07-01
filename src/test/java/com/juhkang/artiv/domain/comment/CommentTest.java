package com.juhkang.artiv.domain.comment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.auth.JwtProvider;
import com.juhkang.artiv.domain.episode.Episode;
import com.juhkang.artiv.domain.episode.EpisodeRepository;
import com.juhkang.artiv.domain.episode.EpisodeStatus;
import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesRepository;
import com.juhkang.artiv.domain.series.SeriesStatus;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

/** 회차 댓글 — 작성/목록/삭제(본인·ADMIN). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private EpisodeRepository episodeRepository;
    @Autowired
    private JwtProvider jwtProvider;

    private String commenterToken;
    private String otherToken;
    private String adminToken;
    private String commentsUrl;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(User.create("creator@t.com", "pw", "작가", Role.CREATOR, ADULT));
        User commenter = userRepository.save(User.create("commenter@t.com", "pw", "댓글러", Role.READER, ADULT));
        User other = userRepository.save(User.create("other@t.com", "pw", "타인", Role.READER, ADULT));
        User admin = userRepository.save(User.create("admin@t.com", "pw", "관리자", Role.ADMIN, ADULT));
        commenterToken = jwtProvider.createAccessToken(commenter.getId(), Role.READER);
        otherToken = jwtProvider.createAccessToken(other.getId(), Role.READER);
        adminToken = jwtProvider.createAccessToken(admin.getId(), Role.ADMIN);

        Series series = seriesRepository.save(Series.create(
                "작품", "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        episodeRepository.save(Episode.create(series, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
        commentsUrl = "/api/series/" + series.getId() + "/episodes/1/comments";
    }

    private long writeComment(String token, String content) throws Exception {
        String body = mockMvc.perform(post(commentsUrl)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Integer) JsonPath.read(body, "$.id")).longValue();
    }

    private long writeReply(String token, String content, long parentId) throws Exception {
        String body = mockMvc.perform(post(commentsUrl)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\",\"parentId\":" + parentId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Integer) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void 댓글_1000자_초과는_500이_아니라_400이다() throws Exception {
        String tooLong = "a".repeat(1001);
        mockMvc.perform(post(commentsUrl)
                        .header("Authorization", "Bearer " + commenterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 댓글을_작성하면_목록에_작성자와_함께_보인다() throws Exception {
        writeComment(commenterToken, "재밌어요");

        mockMvc.perform(get(commentsUrl).header("Authorization", "Bearer " + commenterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].content").value("재밌어요"))
                .andExpect(jsonPath("$.content[0].authorNickname").value("댓글러"));
    }

    @Test
    void 본인은_자기_댓글을_삭제한다_204() throws Exception {
        long id = writeComment(commenterToken, "내댓글");
        mockMvc.perform(delete(commentsUrl + "/" + id).header("Authorization", "Bearer " + commenterToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void 타인은_남의_댓글을_삭제할_수_없다_403() throws Exception {
        long id = writeComment(commenterToken, "내댓글");
        mockMvc.perform(delete(commentsUrl + "/" + id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void 관리자는_타인_댓글을_삭제한다_204() throws Exception {
        long id = writeComment(commenterToken, "내댓글");
        mockMvc.perform(delete(commentsUrl + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    // ── 대댓글(1-depth) ──────────────────────────────────────────────────────
    @Test
    void 대댓글은_부모_아래_중첩되어_내려온다() throws Exception {
        long parentId = writeComment(commenterToken, "부모댓글");
        writeReply(otherToken, "대댓글", parentId);

        mockMvc.perform(get(commentsUrl).header("Authorization", "Bearer " + commenterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)) // 최상위 댓글만 카운트
                .andExpect(jsonPath("$.content[0].content").value("부모댓글"))
                .andExpect(jsonPath("$.content[0].replies.length()").value(1))
                .andExpect(jsonPath("$.content[0].replies[0].content").value("대댓글"))
                .andExpect(jsonPath("$.content[0].replies[0].parentId").value((int) parentId));
    }

    @Test
    void 대댓글에_단_답글은_최상위_부모로_평탄화된다() throws Exception {
        long parentId = writeComment(commenterToken, "부모");
        long replyId = writeReply(otherToken, "대댓글", parentId);
        writeReply(commenterToken, "답글의답글", replyId); // 평탄화 → parentId 로 귀속

        mockMvc.perform(get(commentsUrl).header("Authorization", "Bearer " + commenterToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].replies.length()").value(2))
                .andExpect(jsonPath("$.content[0].replies[1].parentId").value((int) parentId));
    }

    // ── 댓글 좋아요 ───────────────────────────────────────────────────────────
    @Test
    void 댓글_좋아요는_토글되고_수와_내가누른여부가_반영된다() throws Exception {
        long id = writeComment(commenterToken, "좋아요대상");

        mockMvc.perform(post(commentsUrl + "/" + id + "/like").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isCreated());
        mockMvc.perform(get(commentsUrl).header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.content[0].likeCount").value(1))
                .andExpect(jsonPath("$.content[0].liked").value(true));
        // 누르지 않은 뷰어에겐 liked=false, 수는 동일
        mockMvc.perform(get(commentsUrl).header("Authorization", "Bearer " + commenterToken))
                .andExpect(jsonPath("$.content[0].likeCount").value(1))
                .andExpect(jsonPath("$.content[0].liked").value(false));

        mockMvc.perform(delete(commentsUrl + "/" + id + "/like").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(commentsUrl).header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.content[0].likeCount").value(0))
                .andExpect(jsonPath("$.content[0].liked").value(false));
    }

    @Test
    void 같은_댓글_두번_좋아요해도_한번만_집계된다() throws Exception {
        long id = writeComment(commenterToken, "멱등");
        mockMvc.perform(post(commentsUrl + "/" + id + "/like").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isCreated());
        mockMvc.perform(post(commentsUrl + "/" + id + "/like").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isCreated());
        mockMvc.perform(get(commentsUrl).header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.content[0].likeCount").value(1));
    }

    @Test
    void 부모_댓글을_삭제하면_대댓글과_좋아요도_함께_사라진다() throws Exception {
        long parentId = writeComment(commenterToken, "부모");
        long replyId = writeReply(otherToken, "대댓글", parentId);
        // 대댓글 좋아요까지 달아 comment_likes FK 정리 경로를 태운다
        mockMvc.perform(post(commentsUrl + "/" + replyId + "/like").header("Authorization", "Bearer " + commenterToken))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(commentsUrl + "/" + parentId).header("Authorization", "Bearer " + commenterToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(commentsUrl).header("Authorization", "Bearer " + commenterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
