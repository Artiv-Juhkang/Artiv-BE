package com.juhkang.artiv.domain.block;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.auth.JwtProvider;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BlockFlowTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    private String aToken;
    private String bToken;
    private Long aId;
    private Long bId;

    @BeforeEach
    void setUp() {
        User a = userRepository.save(User.create("a@t.com", passwordEncoder.encode("pw"), "가해자아님", Role.READER, ADULT));
        User b = userRepository.save(User.create("b@t.com", passwordEncoder.encode("pw"), "차단대상", Role.READER, ADULT));
        aId = a.getId();
        bId = b.getId();
        aToken = jwtProvider.createAccessToken(a.getId(), Role.READER);
        bToken = jwtProvider.createAccessToken(b.getId(), Role.READER);
    }

    private void createDirect(String token, Long targetId, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/conversations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DIRECT\",\"targetUserId\":" + targetId + "}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void 차단하면_내_차단목록에_반영된다() throws Exception {
        mockMvc.perform(post("/api/users/" + bId + "/block").header("Authorization", "Bearer " + aToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/users/me/blocks").header("Authorization", "Bearer " + aToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nickname").value("차단대상"));
    }

    @Test
    void 차단해제하면_목록에서_사라진다() throws Exception {
        mockMvc.perform(post("/api/users/" + bId + "/block").header("Authorization", "Bearer " + aToken))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/users/" + bId + "/block").header("Authorization", "Bearer " + aToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/me/blocks").header("Authorization", "Bearer " + aToken))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 중복_차단은_멱등이다() throws Exception {
        mockMvc.perform(post("/api/users/" + bId + "/block").header("Authorization", "Bearer " + aToken))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/users/" + bId + "/block").header("Authorization", "Bearer " + aToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/users/me/blocks").header("Authorization", "Bearer " + aToken))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 자기자신은_차단할_수_없다_400() throws Exception {
        mockMvc.perform(post("/api/users/" + aId + "/block").header("Authorization", "Bearer " + aToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_사용자_차단은_404() throws Exception {
        mockMvc.perform(post("/api/users/99999/block").header("Authorization", "Bearer " + aToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 차단한_상대와는_DM을_생성할_수_없다_양방향_403() throws Exception {
        mockMvc.perform(post("/api/users/" + bId + "/block").header("Authorization", "Bearer " + aToken))
                .andExpect(status().isCreated());

        createDirect(aToken, bId, 403); // 차단한 쪽에서 시도
        createDirect(bToken, aId, 403); // 차단당한 쪽에서 시도
    }

    @Test
    void 차단하지_않았으면_DM_생성이_정상_동작한다() throws Exception {
        createDirect(aToken, bId, 201);
    }
}
