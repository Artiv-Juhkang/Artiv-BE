package com.juhkang.apptoon.domain.report;

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
import com.juhkang.apptoon.TestcontainersConfiguration;
import com.juhkang.apptoon.domain.auth.JwtProvider;
import com.juhkang.apptoon.domain.user.Role;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.storage.root=build/test-report-storage")
@AutoConfigureMockMvc
@Transactional
class ReportFlowTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    private String posterToken;
    private String adminToken;
    private final String[] reporters = new String[6];

    @BeforeEach
    void setUp() {
        User poster = userRepository.save(User.create("poster@test.com", passwordEncoder.encode("password123"), "글쓴이", Role.READER, ADULT));
        User admin = userRepository.save(User.create("admin@test.com", passwordEncoder.encode("password123"), "관리자", Role.ADMIN, ADULT));
        posterToken = jwtProvider.createAccessToken(poster.getId(), Role.READER);
        adminToken = jwtProvider.createAccessToken(admin.getId(), Role.ADMIN);
        for (int i = 0; i < reporters.length; i++) {
            User u = userRepository.save(User.create("rep" + i + "@test.com", passwordEncoder.encode("password123"), "신고자" + i, Role.READER, ADULT));
            reporters[i] = jwtProvider.createAccessToken(u.getId(), Role.READER);
        }
    }

    private long createPost() throws Exception {
        String body = mockMvc.perform(multipart("/api/posts")
                        .header("Authorization", "Bearer " + posterToken)
                        .param("category", "FREE").param("title", "신고 대상 글").param("content", "내용"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void report(String token, String targetType, long targetId, int expected) throws Exception {
        mockMvc.perform(post("/api/reports").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"" + targetType + "\",\"targetId\":" + targetId + ",\"reason\":\"SPAM\"}"))
                .andExpect(status().is(expected));
    }

    @Test
    void 글을_신고하면_관리자_큐에_쌓인다() throws Exception {
        long id = createPost();
        report(reporters[0], "POST", id, 201);

        mockMvc.perform(get("/api/admin/reports").param("status", "PENDING")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].targetType").value("POST"));
    }

    @Test
    void 같은_사람은_같은_대상을_중복신고할_수_없다() throws Exception {
        long id = createPost();
        report(reporters[0], "POST", id, 201);
        report(reporters[0], "POST", id, 400);
    }

    @Test
    void 신고가_임계치_5건이면_게시글이_자동_블라인드된다() throws Exception {
        long id = createPost();
        for (int i = 0; i < 5; i++) {
            report(reporters[i], "POST", id, 201);
        }
        // 블라인드 → 공개 목록·상세에서 사라짐
        mockMvc.perform(get("/api/posts").header("Authorization", "Bearer " + posterToken))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/posts/" + id).header("Authorization", "Bearer " + posterToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 관리자가_신고를_처리한다() throws Exception {
        long id = createPost();
        report(reporters[0], "POST", id, 201);
        String body = mockMvc.perform(get("/api/admin/reports").header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        long reportId = ((Number) JsonPath.read(body, "$.content[0].id")).longValue();

        mockMvc.perform(patch("/api/admin/reports/" + reportId + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void 비관리자는_신고_관리에_접근할_수_없다_403() throws Exception {
        mockMvc.perform(get("/api/admin/reports").header("Authorization", "Bearer " + reporters[0]))
                .andExpect(status().isForbidden());
    }
}
