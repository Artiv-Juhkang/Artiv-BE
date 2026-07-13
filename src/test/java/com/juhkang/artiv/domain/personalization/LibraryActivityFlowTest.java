package com.juhkang.artiv.domain.personalization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.auth.JwtProvider;
import com.juhkang.artiv.domain.community.Post;
import com.juhkang.artiv.domain.community.PostComment;
import com.juhkang.artiv.domain.community.PostCommentRepository;
import com.juhkang.artiv.domain.community.PostLike;
import com.juhkang.artiv.domain.community.PostLikeRepository;
import com.juhkang.artiv.domain.community.PostRepository;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LibraryActivityFlowTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private SeriesRepository seriesRepository;
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private ReadLogRepository readLogRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostCommentRepository postCommentRepository;
    @Autowired private PostLikeRepository postLikeRepository;

    private Long meId;
    private String meToken;
    private String otherToken;

    private Long user(String nickname, Role role) {
        return userRepository.save(User.create(nickname + "@lib.com", passwordEncoder.encode("password123"), nickname, role, ADULT)).getId();
    }

    @BeforeEach
    void setUp() {
        meId = user("서재나", Role.READER);
        meToken = jwtProvider.createAccessToken(meId, Role.READER);
        Long other = user("서재남", Role.READER);
        otherToken = jwtProvider.createAccessToken(other, Role.READER);
    }

    @Test
    void 열람한_작품_목록은_작품별로_마지막_본_회차를_보여준다() throws Exception {
        User me = userRepository.findById(meId).orElseThrow();
        User author = userRepository.findById(user("서재작가", Role.CREATOR)).orElseThrow();
        Series series = seriesRepository.save(Series.create("연재물", "설명", author, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        Episode ep1 = episodeRepository.save(Episode.create(series, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
        Episode ep2 = episodeRepository.save(Episode.create(series, 2, "2화", EpisodeStatus.PUBLISHED, Instant.now()));
        readLogRepository.save(ReadLog.create(me, ep1));
        readLogRepository.save(ReadLog.create(me, ep2));

        mockMvc.perform(get("/api/me/read-history").header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].seriesId").value(series.getId()))
                .andExpect(jsonPath("$.content[0].seriesTitle").value("연재물"))
                .andExpect(jsonPath("$.content[0].lastReadEpisodeNo").value(2));
    }

    @Test
    void 임의_sort_파라미터가_있어도_500이_아니라_고정정렬로_응답한다() throws Exception {
        User me = userRepository.findById(meId).orElseThrow();
        User author = userRepository.findById(user("정렬작가", Role.CREATOR)).orElseThrow();
        Series series = seriesRepository.save(Series.create("정렬물", "설명", author, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        readLogRepository.save(ReadLog.create(me, episodeRepository.save(Episode.create(series, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()))));
        postRepository.save(Post.create(meId, "자유", "글", "내용"));
        postLikeRepository.save(PostLike.create(meId, postRepository.save(Post.create(meId, "자유", "추천글", "내용")).getId()));
        postCommentRepository.save(PostComment.create(
                postRepository.save(Post.create(meId, "자유", "원글", "내용")).getId(), meId, null, "댓글"));

        // group-by 비호환(createdAt)·미존재 컬럼(garbage)·유효 컬럼(seriesId) 모두 200(클라 sort 무시, 고정 정렬)
        for (String sort : new String[] {"createdAt,desc", "garbage,desc", "seriesId,asc"}) {
            for (String ep : new String[] {"read-history", "posts", "post-comments", "liked-posts"}) {
                mockMvc.perform(get("/api/me/" + ep).param("sort", sort).header("Authorization", "Bearer " + meToken))
                        .andExpect(status().isOk());
            }
        }
    }

    @Test
    void 내가_쓴_글은_블라인드_포함_플래그와_함께_본인_것만_보인다() throws Exception {
        postRepository.save(Post.create(meId, "자유", "내 글", "내용"));
        Post blinded = Post.create(meId, "자유", "내 블라인드 글", "내용");
        blinded.blind(999L);
        postRepository.save(blinded);
        postRepository.save(Post.create(user("서재타인", Role.READER), "자유", "남의 글", "내용"));

        mockMvc.perform(get("/api/me/posts").header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.title == '내 블라인드 글')].blinded").value(true));
    }

    @Test
    void 내_댓글은_원글_제목과_함께_보인다() throws Exception {
        Long postId = postRepository.save(Post.create(user("글쓴이L", Role.READER), "자유", "원글 제목", "내용")).getId();
        postCommentRepository.save(PostComment.create(postId, meId, null, "내 댓글"));

        mockMvc.perform(get("/api/me/post-comments").header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].content").value("내 댓글"))
                .andExpect(jsonPath("$.content[0].postId").value(postId))
                .andExpect(jsonPath("$.content[0].postTitle").value("원글 제목"));
    }

    @Test
    void 추천한_글_목록은_블라인드_글을_제외한다() throws Exception {
        Long pub = postRepository.save(Post.create(user("작가P", Role.READER), "자유", "공개 글", "내용")).getId();
        Post blindedPost = Post.create(user("작가Q", Role.READER), "자유", "블라인드 글", "내용");
        blindedPost.blind(999L);
        Long blindId = postRepository.save(blindedPost).getId();
        postLikeRepository.save(PostLike.create(meId, pub));
        postLikeRepository.save(PostLike.create(meId, blindId));

        mockMvc.perform(get("/api/me/liked-posts").header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("공개 글"))
                // totalElements는 원본 PostLike 페이지 기준(2건), content는 블라인드 제외(1건) — 의도된 비대칭
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void 활동내역은_본인_것만_보인다_IDOR() throws Exception {
        postRepository.save(Post.create(meId, "자유", "내 글", "내용"));
        // 다른 사용자가 조회하면 내 글이 보이지 않는다
        mockMvc.perform(get("/api/me/posts").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
