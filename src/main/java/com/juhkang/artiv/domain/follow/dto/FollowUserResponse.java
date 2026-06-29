package com.juhkang.artiv.domain.follow.dto;

/** 팔로잉/팔로워 목록 항목. */
public record FollowUserResponse(
        Long userId,
        String nickname,
        String avatarUrl
) {
}
