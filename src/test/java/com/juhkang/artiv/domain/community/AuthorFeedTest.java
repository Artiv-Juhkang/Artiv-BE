package com.juhkang.artiv.domain.community;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
import com.juhkang.artiv.domain.follow.FollowService;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthorFeedTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private FollowService followService;
    @Autowired private PostRepository postRepository;

    private Long meId;
    private String meToken;
    private Long authorA;

    private Long user(String nickname) {
        return userRepository.save(User.create(nickname + "@feed.com", passwordEncoder.encode("password123"), nickname, Role.CREATOR, ADULT)).getId();
    }

    private Long post(Long authorId, String title, boolean blinded) {
        Post p = Post.create(authorId, PostCategory.FREE, title, "내용");
        if (blinded) {
            p.blind(999L);
        }
        return postRepository.save(p).getId();
    }

    @BeforeEach
    void setUp() {
        meId = user("피드나");
        meToken = jwtProvider.createAccessToken(meId, Role.READER);
        authorA = user("작가가");
    }

    @Test
    void 소식피드는_팔로우한_작가의_공개글만_최신순으로_보여준다() throws Exception {
        Long authorB = user("작가나");
        Long stranger = user("안친한작가");
        followService.follow(meId, authorA);
        followService.follow(meId, authorB);

        post(authorA, "A의 글", false);
        post(authorA, "A의 블라인드", true);   // 블라인드 제외
        long bPost = post(authorB, "B의 글", false);
        post(stranger, "모르는 작가 글", false); // 팔로우 안 함 → 제외

        mockMvc.perform(get("/api/me/author-news-feed").header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value((int) bPost)); // 최신순(id desc)
    }

    @Test
    void 아무도_팔로우_안하면_소식피드는_빈다() throws Exception {
        post(authorA, "A의 글", false);
        mockMvc.perform(get("/api/me/author-news-feed").header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 작가별_공개글_조회는_블라인드를_제외한다() throws Exception {
        post(authorA, "공개 글", false);
        post(authorA, "블라인드 글", true);

        mockMvc.perform(get("/api/authors/" + authorA + "/posts").header("Authorization", "Bearer " + meToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("공개 글"))
                .andExpect(jsonPath("$.content[0].authorNickname").value("작가가"));
    }
}
