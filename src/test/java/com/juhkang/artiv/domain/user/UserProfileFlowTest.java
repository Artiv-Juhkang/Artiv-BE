package com.juhkang.artiv.domain.user;

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

import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.auth.JwtProvider;

/** 공개 프로필 열람(GET /api/users/{id}) + 공개설정 + 팔로우 상호 판정 (CH1). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserProfileFlowTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    private String viewerToken;
    private String targetToken;
    private Long viewerId;
    private Long targetId;

    @BeforeEach
    void setUp() {
        User viewer = userRepository.save(User.create("viewer@t.com", passwordEncoder.encode("pw"), "관람자", Role.READER, ADULT));
        User target = userRepository.save(User.create("target@t.com", passwordEncoder.encode("pw"), "미루작가", Role.CREATOR, ADULT));
        target.changeBio("손그림을 그립니다");
        viewerId = viewer.getId();
        targetId = target.getId();
        viewerToken = jwtProvider.createAccessToken(viewer.getId(), Role.READER);
        targetToken = jwtProvider.createAccessToken(target.getId(), Role.CREATOR);
    }

    @Test
    void 공개_프로필은_타인이_전체를_본다() throws Exception {
        mockMvc.perform(get("/api/users/" + targetId).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetId.intValue()))
                .andExpect(jsonPath("$.nickname").value("미루작가"))
                .andExpect(jsonPath("$.role").value("CREATOR"))
                .andExpect(jsonPath("$.profilePublic").value(true))
                .andExpect(jsonPath("$.bio").value("손그림을 그립니다"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void 비공개_전환하면_타인에게_bio와_가입일이_숨는다() throws Exception {
        // 본인이 비공개 전환 — 응답에 profilePublic 반영
        mockMvc.perform(patch("/api/users/me/profile-visibility")
                        .header("Authorization", "Bearer " + targetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profilePublic\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePublic").value(false));

        // 타인 조회 — 닉네임·역할은 항상 공개(댓글·채팅 표기에 필수), bio·가입일만 숨김(확정 D-확3)
        mockMvc.perform(get("/api/users/" + targetId).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("미루작가"))
                .andExpect(jsonPath("$.profilePublic").value(false))
                .andExpect(jsonPath("$.bio").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist());

        // 다시 공개 전환 → 복원
        mockMvc.perform(patch("/api/users/me/profile-visibility")
                        .header("Authorization", "Bearer " + targetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profilePublic\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/" + targetId).header("Authorization", "Bearer " + viewerToken))
                .andExpect(jsonPath("$.bio").value("손그림을 그립니다"));
    }

    @Test
    void 미존재_사용자는_404() throws Exception {
        mockMvc.perform(get("/api/users/999999").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 팔로우_스탯에_상호_여부가_실린다() throws Exception {
        // 단방향: viewer → target
        mockMvc.perform(post("/api/users/" + targetId + "/follow").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/users/" + targetId + "/follow-stats").header("Authorization", "Bearer " + viewerToken))
                .andExpect(jsonPath("$.isFollowing").value(true))
                .andExpect(jsonPath("$.isFollowedBy").value(false))
                .andExpect(jsonPath("$.isMutual").value(false));

        // 역방향 추가: target → viewer = 상호(친구)
        mockMvc.perform(post("/api/users/" + viewerId + "/follow").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/users/" + targetId + "/follow-stats").header("Authorization", "Bearer " + viewerToken))
                .andExpect(jsonPath("$.isFollowing").value(true))
                .andExpect(jsonPath("$.isFollowedBy").value(true))
                .andExpect(jsonPath("$.isMutual").value(true));
    }

    @Test
    void 비공개_프로필의_팔로우_수는_타인에게_숨는다() throws Exception {
        // target을 팔로우해 실제 카운트를 1로 만든다.
        mockMvc.perform(post("/api/users/" + targetId + "/follow").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/users/" + targetId + "/follow-stats").header("Authorization", "Bearer " + viewerToken))
                .andExpect(jsonPath("$.followerCount").value(1));

        // target 비공개 전환 — 확3: bio·가입일·팔로우 수만 숨김(관계 플래그는 조회자 자신의 것이라 유지).
        mockMvc.perform(patch("/api/users/me/profile-visibility")
                        .header("Authorization", "Bearer " + targetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profilePublic\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/" + targetId + "/follow-stats").header("Authorization", "Bearer " + viewerToken))
                .andExpect(jsonPath("$.followerCount").value(0))
                .andExpect(jsonPath("$.followingCount").value(0))
                .andExpect(jsonPath("$.isFollowing").value(true)); // 관계 플래그는 유지

        // 본인 자신의 스탯 조회는 비공개여도 실제 카운트를 본다.
        mockMvc.perform(get("/api/users/" + targetId + "/follow-stats").header("Authorization", "Bearer " + targetToken))
                .andExpect(jsonPath("$.followerCount").value(1));
    }
}
