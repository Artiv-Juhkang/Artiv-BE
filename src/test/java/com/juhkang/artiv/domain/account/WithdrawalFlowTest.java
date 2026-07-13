package com.juhkang.artiv.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesRepository;
import com.juhkang.artiv.domain.series.SeriesStatus;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WithdrawalFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    void 비밀번호가_틀리면_탈퇴가_거부된다() throws Exception {
        User user = userRepository.save(
                User.create("wrongpw@test.com", passwordEncoder.encode("password123"), "틀린비번", Role.READER, null));
        String accessToken = objectMapper.readTree(login("wrongpw@test.com", "password123")).get("accessToken").asString();

        mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"nope\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 탈퇴하면_로그인과_리프레시가_모두_거부되고_같은_이메일로_재가입할_수_있다() throws Exception {
        userRepository.save(
                User.create("bye@test.com", passwordEncoder.encode("password123"), "떠날사람", Role.READER, null));
        String body = login("bye@test.com", "password123");
        String accessToken = objectMapper.readTree(body).get("accessToken").asString();
        String refreshToken = objectMapper.readTree(body).get("refreshToken").asString();

        mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isNoContent());

        // 탈퇴 직후: 이메일이 센티널로 바뀌었으므로 같은 이메일 로그인은 더 이상 성립하지 않는다.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bye@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());

        // 이미 발급된 refresh 토큰도 탈퇴한 사용자 것이라 거부된다.
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        // 이메일이 비었으니 같은 이메일로 재가입할 수 있다.
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bye@test.com\",\"password\":\"newpassword123\",\"nickname\":\"새사람\","
                                + "\"birthDate\":\"2000-01-01\","
                                + "\"consents\":{\"TERMS_OF_SERVICE\":true,\"PRIVACY_POLICY\":true}}"))
                .andExpect(status().isCreated());
    }

    @Test
    void 작가가_탈퇴하면_작품이_전부_비공개로_전환된다() throws Exception {
        User creator = userRepository.save(
                User.create("creator-bye@test.com", passwordEncoder.encode("password123"), "떠나는작가", Role.CREATOR, null));
        Series series = seriesRepository.save(Series.create(
                "작가의 마지막 작품", "설명", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));

        String accessToken = objectMapper.readTree(login("creator-bye@test.com", "password123"))
                .get("accessToken").asString();

        mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isNoContent());

        boolean visible = seriesRepository.findById(series.getId()).orElseThrow().isVisible();
        assertThat(visible).isFalse();
    }
}
