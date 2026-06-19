package com.juhkang.apptoon.domain.comment;

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
import com.juhkang.apptoon.TestcontainersConfiguration;
import com.juhkang.apptoon.domain.auth.JwtProvider;
import com.juhkang.apptoon.domain.episode.Episode;
import com.juhkang.apptoon.domain.episode.EpisodeRepository;
import com.juhkang.apptoon.domain.episode.EpisodeStatus;
import com.juhkang.apptoon.domain.series.AgeRating;
import com.juhkang.apptoon.domain.series.Series;
import com.juhkang.apptoon.domain.series.SeriesRepository;
import com.juhkang.apptoon.domain.series.SeriesStatus;
import com.juhkang.apptoon.domain.user.Role;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;

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
}
