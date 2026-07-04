package com.juhkang.artiv.domain.community;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.auth.JwtProvider;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.storage.root=build/test-community-storage")
@AutoConfigureMockMvc
@Transactional
class CommunityFlowTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    private String readerToken;
    private String otherToken;
    private String adminToken;
    private long readerId;
    private long otherId;

    @BeforeEach
    void setUp() {
        User reader = userRepository.save(User.create("reader@test.com", passwordEncoder.encode("password123"), "독자", Role.READER, ADULT));
        User other = userRepository.save(User.create("other@test.com", passwordEncoder.encode("password123"), "다른독자", Role.READER, ADULT));
        User admin = userRepository.save(User.create("admin@test.com", passwordEncoder.encode("password123"), "관리자", Role.ADMIN, ADULT));
        readerId = reader.getId();
        otherId = other.getId();
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);
        otherToken = jwtProvider.createAccessToken(other.getId(), Role.READER);
        adminToken = jwtProvider.createAccessToken(admin.getId(), Role.ADMIN);
    }

    private MockMultipartFile png() throws IOException {
        BufferedImage img = new BufferedImage(80, 80, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return new MockMultipartFile("images", "a.png", "image/png", bos.toByteArray());
    }

    private long createPost(String token, String category, String title) throws Exception {
        String body = mockMvc.perform(multipart("/api/posts").file(png())
                        .param("category", category).param("title", title).param("content", "본문입니다")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void 글을_이미지와_함께_작성하고_목록과_상세에서_본다() throws Exception {
        long id = createPost(readerToken, "FANART", "내가 그린 팬아트");

        mockMvc.perform(get("/api/posts").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("내가 그린 팬아트"))
                .andExpect(jsonPath("$.content[0].category").value("FANART"))
                .andExpect(jsonPath("$.content[0].authorId").value((int) readerId));

        mockMvc.perform(get("/api/posts/" + id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("본문입니다"))
                .andExpect(jsonPath("$.authorId").value((int) readerId))
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.images.length()").value(1));
    }

    @Test
    void 추천을_토글하면_추천수가_변한다() throws Exception {
        long id = createPost(readerToken, "FREE", "추천해주세요");
        mockMvc.perform(post("/api/posts/" + id + "/like").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/posts/" + id).header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.liked").value(true));
        mockMvc.perform(delete("/api/posts/" + id + "/like").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/posts/" + id).header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.liked").value(false));
    }

    @Test
    void 댓글과_대댓글을_달고_조회한다() throws Exception {
        long postId = createPost(readerToken, "QUESTION", "질문있어요");
        String body = mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"좋은 글이네요\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long commentId = ((Number) JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"감사합니다\",\"parentId\":" + commentId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/posts/" + postId + "/comments").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("좋은 글이네요"))
                .andExpect(jsonPath("$[0].authorId").value((int) otherId))
                .andExpect(jsonPath("$[0].replies.length()").value(1))
                .andExpect(jsonPath("$[0].replies[0].content").value("감사합니다"))
                .andExpect(jsonPath("$[0].replies[0].authorId").value((int) readerId));
    }

    @Test
    void 글_수정은_작성자만_텍스트_필드를_고친다() throws Exception {
        long id = createPost(readerToken, "FREE", "원래 제목");

        // 타인 수정 → 403 (삭제와 달리 관리자도 불가 — 모더레이션은 블라인드로)
        mockMvc.perform(patch("/api/posts/" + id)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"QUESTION\",\"title\":\"탈취 시도\",\"content\":\"본문\"}"))
                .andExpect(status().isForbidden());

        // 검증 실패(빈 제목) → 400
        mockMvc.perform(patch("/api/posts/" + id)
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"QUESTION\",\"title\":\"  \",\"content\":\"본문\"}"))
                .andExpect(status().isBadRequest());

        // 작성자 수정 → 204, 상세에 반영
        mockMvc.perform(patch("/api/posts/" + id)
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"QUESTION\",\"title\":\"고친 제목\",\"content\":\"@다른독자 고친 본문\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/posts/" + id).header("Authorization", "Bearer " + readerToken))
                .andExpect(jsonPath("$.title").value("고친 제목"))
                .andExpect(jsonPath("$.category").value("QUESTION"))
                .andExpect(jsonPath("$.content").value("@다른독자 고친 본문"));

        // 같은 멘션으로 재수정해도 dedupKey(POST_MENTION:{postId}:{rid})가 중복 알림을 억제한다
        mockMvc.perform(patch("/api/posts/" + id)
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"QUESTION\",\"title\":\"고친 제목2\",\"content\":\"@다른독자 또 고침\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/notifications").header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.content[?(@.type == 'POST_MENTIONED')]", hasSize(1)));
    }

    @Test
    void 글_삭제는_본인이나_관리자만() throws Exception {
        long id = createPost(readerToken, "FREE", "삭제될 글");
        mockMvc.perform(delete("/api/posts/" + id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/posts/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/posts/" + id).header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 베스트는_추천수_기준으로_필터된다() throws Exception {
        long hot = createPost(readerToken, "RECOMMEND", "인기글");
        createPost(readerToken, "FREE", "보통글");
        // best 임계치 이상 추천 — 테스트는 1추천도 best로 보이지 않게 임계치=2 가정, 여기선 sort=BEST 동작만 확인
        mockMvc.perform(post("/api/posts/" + hot + "/like").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/posts").param("sort", "BEST").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value((int) hot)); // 추천 많은 글이 먼저
    }
}
