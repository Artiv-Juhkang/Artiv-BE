package com.juhkang.apptoon.domain.follow;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.apptoon.TestcontainersConfiguration;
import com.juhkang.apptoon.domain.auth.JwtProvider;
import com.juhkang.apptoon.domain.user.Role;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FollowFlowTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    private String readerToken;
    private String authorToken;
    private Long readerId;
    private Long authorId;

    @BeforeEach
    void setUp() {
        User reader = userRepository.save(User.create("reader@test.com", passwordEncoder.encode("password123"), "독자", Role.READER, ADULT));
        User author = userRepository.save(User.create("author@test.com", passwordEncoder.encode("password123"), "작가", Role.CREATOR, ADULT));
        readerId = reader.getId();
        authorId = author.getId();
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);
        authorToken = jwtProvider.createAccessToken(author.getId(), Role.CREATOR);
    }

    @Test
    void 팔로우하면_내_팔로잉_상대_팔로워_통계에_반영된다() throws Exception {
        mockMvc.perform(post("/api/users/" + authorId + "/follow").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/users/me/following").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nickname").value("작가"));

        mockMvc.perform(get("/api/users/me/followers").header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nickname").value("독자"));

        mockMvc.perform(get("/api/users/" + authorId + "/follow-stats").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerCount").value(1))
                .andExpect(jsonPath("$.isFollowing").value(true));
    }

    @Test
    void 언팔로우하면_사라진다() throws Exception {
        mockMvc.perform(post("/api/users/" + authorId + "/follow").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/users/" + authorId + "/follow").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/me/following").header("Authorization", "Bearer " + readerToken))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/users/" + authorId + "/follow-stats").header("Authorization", "Bearer " + readerToken))
                .andExpect(jsonPath("$.followerCount").value(0))
                .andExpect(jsonPath("$.isFollowing").value(false));
    }

    @Test
    void 중복_팔로우는_멱등이다() throws Exception {
        mockMvc.perform(post("/api/users/" + authorId + "/follow").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/users/" + authorId + "/follow").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/users/" + authorId + "/follow-stats").header("Authorization", "Bearer " + readerToken))
                .andExpect(jsonPath("$.followerCount").value(1));
    }

    @Test
    void 자기자신은_팔로우할_수_없다_400() throws Exception {
        mockMvc.perform(post("/api/users/" + readerId + "/follow").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_사용자_팔로우는_404() throws Exception {
        mockMvc.perform(post("/api/users/99999/follow").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isNotFound());
    }
}
