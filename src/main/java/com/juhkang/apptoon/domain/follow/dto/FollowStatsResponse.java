package com.juhkang.apptoon.domain.follow.dto;

/** 특정 사용자의 팔로우 통계 + 내가 팔로우 중인지. */
public record FollowStatsResponse(
        long followerCount,
        long followingCount,
        boolean isFollowing
) {
}
