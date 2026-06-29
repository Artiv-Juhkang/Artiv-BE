package com.juhkang.artiv.domain.user.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;

/**
 * 본인 프로필(/api/users/me) — 관리자용 UserResponse와 달리 본인이므로 birthDate·bio·avatar 포함.
 * avatarUrl은 저장 key를 스토리지 URL로 변환해 서비스에서 채운다.
 */
public record MyProfileResponse(
        Long id,
        String email,
        String nickname,
        Role role,
        String bio,
        String avatarUrl,
        LocalDate birthDate,
        Instant createdAt
) {

    public static MyProfileResponse of(User user, String avatarUrl) {
        return new MyProfileResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole(),
                user.getBio(), avatarUrl, user.getBirthDate(), user.getCreatedAt());
    }
}
