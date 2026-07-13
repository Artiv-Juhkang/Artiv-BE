package com.juhkang.artiv.domain.block.dto;

public record BlockedUserResponse(
        Long userId,
        String nickname,
        String avatarUrl
) {
}
