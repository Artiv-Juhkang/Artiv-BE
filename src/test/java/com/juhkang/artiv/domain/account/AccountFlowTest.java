package com.juhkang.artiv.domain.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.juhkang.artiv.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.storage.root=build/test-account-storage")
@AutoConfigureMockMvc
@Transactional
class AccountFlowTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String CONSENTS_OK =
            "\"consents\":{\"TERMS_OF_SERVICE\":true,\"PRIVACY_POLICY\":true,\"MARKETING_EMAIL\":false}";

    private void signup(String email, String birthDate, String consentsPart, int expectedStatus) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\",\"nickname\":\"테스터\","
                + "\"birthDate\":\"" + birthDate + "\"" + (consentsPart.isEmpty() ? "" : "," + consentsPart) + "}";
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
    }

    private String token(String email, String birthDate) throws Exception {
        signup(email, birthDate, CONSENTS_OK, 201);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return (String) JsonPath.read(body, "$.accessToken");
    }

    private MockMultipartFile png() throws IOException {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return new MockMultipartFile("file", "a.png", "image/png", bos.toByteArray());
    }

    @Test
    void 필수동의_미동의시_가입실패_400() throws Exception {
        signup("a@test.com", "1990-01-01",
                "\"consents\":{\"TERMS_OF_SERVICE\":false,\"PRIVACY_POLICY\":true}", 400);
    }

    @Test
    void 만14세_미만은_가입할_수_없다_400() throws Exception {
        signup("kid@test.com", "2020-01-01", CONSENTS_OK, 400);
    }

    @Test
    void 동의하고_가입하면_동의내역이_조회된다() throws Exception {
        String t = token("ok@test.com", "1990-01-01");
        mockMvc.perform(get("/api/users/me/consents").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.consentType=='TERMS_OF_SERVICE')].agreed", contains(true)))
                .andExpect(jsonPath("$[?(@.consentType=='MARKETING_EMAIL')].agreed", contains(false)));
    }

    @Test
    void 프로필_조회는_본인_생년월일과_소개를_포함한다() throws Exception {
        String t = token("prof@test.com", "1990-01-01");
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("prof@test.com"))
                .andExpect(jsonPath("$.birthDate").value("1990-01-01"));
    }

    @Test
    void 닉네임과_소개를_변경한다() throws Exception {
        String t = token("edit@test.com", "1990-01-01");
        mockMvc.perform(patch("/api/users/me/nickname").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"새이름\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/users/me/bio").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"안녕하세요 작가입니다\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.nickname").value("새이름"))
                .andExpect(jsonPath("$.bio").value("안녕하세요 작가입니다"));
    }

    @Test
    void 비밀번호_변경은_현재비번이_틀리면_실패하고_맞으면_성공한다() throws Exception {
        String t = token("pw@test.com", "1990-01-01");
        mockMvc.perform(patch("/api/users/me/password").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrongpass\",\"newPassword\":\"newpassword123\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/users/me/password").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword123\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pw@test.com\",\"password\":\"newpassword123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 마케팅_동의를_토글한다() throws Exception {
        String t = token("mk@test.com", "1990-01-01");
        mockMvc.perform(patch("/api/users/me/consents").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consents\":{\"MARKETING_EMAIL\":true}}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me/consents").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$[?(@.consentType=='MARKETING_EMAIL')].agreed", contains(true)));
    }

    @Test
    void 필수동의는_철회할_수_없다_400() throws Exception {
        String t = token("req@test.com", "1990-01-01");
        mockMvc.perform(patch("/api/users/me/consents").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consents\":{\"TERMS_OF_SERVICE\":false}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 아바타를_업로드하면_프로필에_반영된다() throws Exception {
        String t = token("av@test.com", "1990-01-01");
        mockMvc.perform(multipart("/api/users/me/avatar").file(png())
                        .header("Authorization", "Bearer " + t))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.avatarUrl").isString());
    }
}
