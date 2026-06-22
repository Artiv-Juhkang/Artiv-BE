package com.juhkang.apptoon.domain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.juhkang.apptoon.TestcontainersConfiguration;

import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private void signup(String email, String password, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"nickname\":\"" + nickname
                                + "\",\"birthDate\":\"2000-01-01\",\"consents\":{\"TERMS_OF_SERVICE\":true,\"PRIVACY_POLICY\":true}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void 가입_로그인_후_액세스토큰으로_보호된_API에_접근한다() throws Exception {
        signup("flow@test.com", "password123", "플로우");

        String body = login("flow@test.com", "password123");
        String accessToken = objectMapper.readTree(body).get("accessToken").asString();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("flow@test.com"))
                .andExpect(jsonPath("$.role").value("READER"));
    }

    @Test
    void 토큰_없이_보호된_API_접근은_401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 공백_포함_닉네임_가입은_400() throws Exception {  // 멘션 불가 닉네임 차단(멘션 문자셋과 일치)
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"space@test.com\",\"password\":\"password123\",\"nickname\":\"공백 닉\","
                                + "\"birthDate\":\"2000-01-01\",\"consents\":{\"TERMS_OF_SERVICE\":true,\"PRIVACY_POLICY\":true}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 잘못된_토큰은_401() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer garbage.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 틀린_비밀번호_로그인은_INVALID_CREDENTIALS_401() throws Exception {
        signup("wrong@test.com", "password123", "닉_flow");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrong@test.com\",\"password\":\"wrongpass1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 리프레시_토큰으로_새_토큰쌍을_발급받는다() throws Exception {
        signup("refresh@test.com", "password123", "리프");
        String refreshToken = objectMapper.readTree(login("refresh@test.com", "password123")).get("refreshToken").asString();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());

        // 회전: 사용된 리프레시 토큰은 재사용 불가(401)
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
