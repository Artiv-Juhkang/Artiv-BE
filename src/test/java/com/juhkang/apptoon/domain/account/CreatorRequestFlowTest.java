package com.juhkang.apptoon.domain.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CreatorRequestFlowTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    private String readerToken;
    private Long readerId;
    private String creatorToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        User reader = userRepository.save(User.create("reader@test.com", passwordEncoder.encode("password123"), "독자", Role.READER, ADULT));
        User creator = userRepository.save(User.create("creator@test.com", passwordEncoder.encode("password123"), "작가", Role.CREATOR, ADULT));
        User admin = userRepository.save(User.create("admin@test.com", passwordEncoder.encode("password123"), "관리자", Role.ADMIN, ADULT));
        readerId = reader.getId();
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);
        creatorToken = jwtProvider.createAccessToken(creator.getId(), Role.CREATOR);
        adminToken = jwtProvider.createAccessToken(admin.getId(), Role.ADMIN);
    }

    private long submit(String token) throws Exception {
        String body = mockMvc.perform(post("/api/users/me/creator-request")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"웹툰을 연재하고 싶어요\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void 독자가_작가전환을_신청하고_관리자가_승인하면_작가가_된다() throws Exception {
        long id = submit(readerToken);

        mockMvc.perform(get("/api/users/me/creator-request").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/admin/creator-requests").param("status", "PENDING")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(patch("/api/admin/creator-requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + readerToken))
                .andExpect(jsonPath("$.role").value("CREATOR"));
    }

    @Test
    void 관리자가_거부하면_REJECTED가_되고_역할은_그대로다() throws Exception {
        long id = submit(readerToken);
        mockMvc.perform(patch("/api/admin/creator-requests/" + id + "/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"adminNote\":\"포트폴리오가 필요해요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + readerToken))
                .andExpect(jsonPath("$.role").value("READER"));
    }

    @Test
    void 이미_신청중이면_중복신청할_수_없다_400() throws Exception {
        submit(readerToken);
        mockMvc.perform(post("/api/users/me/creator-request")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"또 신청\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 이미_작가면_신청할_수_없다_400() throws Exception {
        mockMvc.perform(post("/api/users/me/creator-request")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"신청\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 비관리자는_작가신청_관리에_접근할_수_없다_403() throws Exception {
        mockMvc.perform(get("/api/admin/creator-requests").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isForbidden());
    }
}
