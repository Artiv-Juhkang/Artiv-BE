package com.juhkang.artiv.domain.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
@SpringBootTest(properties = "app.storage.root=build/test-moderation-storage")
@AutoConfigureMockMvc
@Transactional
class ReportModerationTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    private String posterToken;
    private String reporterToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        User poster = userRepository.save(User.create("poster@test.com", passwordEncoder.encode("password123"), "글쓴이", Role.READER, ADULT));
        User reporter = userRepository.save(User.create("rep@test.com", passwordEncoder.encode("password123"), "신고자", Role.READER, ADULT));
        User admin = userRepository.save(User.create("admin@test.com", passwordEncoder.encode("password123"), "관리자", Role.ADMIN, ADULT));
        posterToken = jwtProvider.createAccessToken(poster.getId(), Role.READER);
        reporterToken = jwtProvider.createAccessToken(reporter.getId(), Role.READER);
        adminToken = jwtProvider.createAccessToken(admin.getId(), Role.ADMIN);
    }

    private long createPost(String title, String category) throws Exception {
        String body = mockMvc.perform(multipart("/api/posts").header("Authorization", "Bearer " + posterToken)
                        .param("category", category).param("title", title).param("content", "문제가 되는 내용"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long reportPost(long postId) throws Exception {
        mockMvc.perform(post("/api/reports").header("Authorization", "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":" + postId + ",\"reason\":\"ABUSE\",\"detail\":\"욕설 포함\"}"))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(get("/api/admin/reports").param("status", "PENDING")
                        .header("Authorization", "Bearer " + adminToken)).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.content[0].id")).longValue();
    }

    @Test
    void 신고_상세에_대상_게시글_내용이_보인다() throws Exception {
        long postId = createPost("문제글", "자유");
        long reportId = reportPost(postId);

        mockMvc.perform(get("/api/admin/reports/" + reportId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetType").value("POST"))
                .andExpect(jsonPath("$.targetContent").value("문제가 되는 내용"))
                .andExpect(jsonPath("$.targetAuthorNickname").value("글쓴이"))
                .andExpect(jsonPath("$.reason").value("ABUSE"));
    }

    @Test
    void 관리자가_신고를_처리하며_대상을_블라인드한다() throws Exception {
        long postId = createPost("블라인드될글", "자유");
        long reportId = reportPost(postId);

        mockMvc.perform(patch("/api/admin/reports/" + reportId + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"BLIND_TARGET\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        // 블라인드되어 공개 상세에서 사라짐
        mockMvc.perform(get("/api/posts/" + postId).header("Authorization", "Bearer " + reporterToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 관리자가_신고를_처리하며_대상을_삭제한다() throws Exception {
        long postId = createPost("삭제될글", "자유");
        long reportId = reportPost(postId);

        mockMvc.perform(patch("/api/admin/reports/" + reportId + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"DELETE_TARGET\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/posts/" + postId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 신고를_대상종류와_사유로_필터링한다() throws Exception {
        reportPost(createPost("글1", "자유"));
        mockMvc.perform(get("/api/admin/reports").param("targetType", "POST").param("reason", "ABUSE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/admin/reports").param("reason", "SPAM")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 커뮤니티_관리에서_키워드와_카테고리로_검색하고_상세를_본다() throws Exception {
        long id = createPost("회귀 무신 추천", "추천");
        createPost("일상 잡담", "자유");

        mockMvc.perform(get("/api/admin/posts").param("keyword", "회귀")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("회귀 무신 추천"));
        mockMvc.perform(get("/api/admin/posts").param("category", "자유")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/admin/posts/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("문제가 되는 내용"))
                .andExpect(jsonPath("$.authorNickname").value("글쓴이"));
    }
}
