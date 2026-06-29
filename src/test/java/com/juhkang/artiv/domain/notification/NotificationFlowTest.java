package com.juhkang.artiv.domain.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationFlowTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    private String userToken;   // 문의 작성자(알림 수신자)
    private String otherToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.create("user@test.com", passwordEncoder.encode("password123"), "사용자", Role.CREATOR, ADULT));
        User other = userRepository.save(User.create("other@test.com", passwordEncoder.encode("password123"), "남", Role.READER, ADULT));
        User admin = userRepository.save(User.create("admin@test.com", passwordEncoder.encode("password123"), "관리자", Role.ADMIN, ADULT));
        userToken = jwtProvider.createAccessToken(user.getId(), Role.CREATOR);
        otherToken = jwtProvider.createAccessToken(other.getId(), Role.READER);
        adminToken = jwtProvider.createAccessToken(admin.getId(), Role.ADMIN);
    }

    /** 문의 작성 후 관리자 답변 → 작성자에게 INQUIRY_ANSWERED 알림. 문의 id 반환. */
    private long answerAnInquiry() throws Exception {
        String body = mockMvc.perform(multipart("/api/me/inquiries")
                        .param("type", "ETC").param("title", "문의합니다").param("content", "내용")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long inquiryId = ((Number) JsonPath.read(body, "$.id")).longValue();
        mockMvc.perform(patch("/api/admin/inquiries/" + inquiryId + "/answer")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"처리했어요\"}"))
                .andExpect(status().isOk());
        return inquiryId;
    }

    @Test
    void 문의에_답변하면_작성자에게_알림이_쌓이고_라우팅정보를_담는다() throws Exception {
        long inquiryId = answerAnInquiry();

        mockMvc.perform(get("/api/me/notifications").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("INQUIRY_ANSWERED"))
                .andExpect(jsonPath("$.content[0].targetType").value("INQUIRY"))
                .andExpect(jsonPath("$.content[0].targetId").value((int) inquiryId))
                .andExpect(jsonPath("$.content[0].read").value(false));
    }

    @Test
    void 미읽음_개수와_종류별_집계를_조회한다() throws Exception {
        answerAnInquiry();
        mockMvc.perform(get("/api/me/notifications/unread-count").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/api/me/notifications/unread-summary").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.byType.INQUIRY_ANSWERED").value(1));
    }

    @Test
    void 종류별로_필터링한다() throws Exception {
        answerAnInquiry();
        mockMvc.perform(get("/api/me/notifications").param("type", "INQUIRY_ANSWERED").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/me/notifications").param("type", "EPISODE_PUBLISHED").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 읽음처리하면_라우팅정보를_반환하고_미읽음이_준다() throws Exception {
        long inquiryId = answerAnInquiry();
        String body = mockMvc.perform(get("/api/me/notifications").header("Authorization", "Bearer " + userToken))
                .andReturn().getResponse().getContentAsString();
        long nid = ((Number) JsonPath.read(body, "$.content[0].id")).longValue();

        mockMvc.perform(patch("/api/me/notifications/" + nid + "/read").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.targetType").value("INQUIRY"))
                .andExpect(jsonPath("$.targetId").value((int) inquiryId));

        mockMvc.perform(get("/api/me/notifications/unread-count").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void 전체_읽음처리한다() throws Exception {
        answerAnInquiry();
        mockMvc.perform(patch("/api/me/notifications/read-all").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/notifications/unread-count").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void 남의_알림은_읽음처리할_수_없다_403() throws Exception {
        answerAnInquiry();
        String body = mockMvc.perform(get("/api/me/notifications").header("Authorization", "Bearer " + userToken))
                .andReturn().getResponse().getContentAsString();
        long nid = ((Number) JsonPath.read(body, "$.content[0].id")).longValue();
        mockMvc.perform(patch("/api/me/notifications/" + nid + "/read").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void 같은_문의_답변_재호출은_알림을_중복생성하지_않는다() throws Exception {
        long inquiryId = answerAnInquiry();
        mockMvc.perform(patch("/api/admin/inquiries/" + inquiryId + "/answer")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"다시 답변\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me/notifications").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.totalElements").value(1)); // dedup
    }
}
