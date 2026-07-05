package com.juhkang.artiv.domain.follow.dto;

/** 특정 사용자의 팔로우 통계 + 나와의 관계(팔로우/맞팔로우/상호=친구). */
public record FollowStatsResponse(
        long followerCount,
        long followingCount,
        boolean isFollowing,
        boolean isFollowedBy,
        boolean isMutual
) {
}
