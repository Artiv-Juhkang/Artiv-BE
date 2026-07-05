package com.juhkang.artiv.domain.user.dto;

import java.time.Instant;

import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;

/**
 * 공개 프로필(GET /api/users/{id}) — 타인 열람용 (CH1).
 * 닉네임·아바타·역할은 항상 공개(댓글·채팅 표기에 필수), bio·가입일은 비공개 시 null(확정 D-확3).
 */
public record UserProfileResponse(
        Long id,
        String nickname,
        String avatarUrl,
        Role role,
        boolean profilePublic,
        String bio,
        Instant createdAt
) {

    public static UserProfileResponse of(User user, String avatarUrl) {
        boolean open = user.isProfilePublic();
        return new UserProfileResponse(user.getId(), user.getNickname(), avatarUrl, user.getRole(),
                open, open ? user.getBio() : null, open ? user.getCreatedAt() : null);
    }
}
