package com.juhkang.apptoon.domain.follow;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.apptoon.domain.follow.dto.FollowStatsResponse;
import com.juhkang.apptoon.domain.follow.dto.FollowUserResponse;

import lombok.RequiredArgsConstructor;

/** 팔로우 — 사용자(작가) 팔로우/언팔로우, 내 팔로잉·팔로워, 통계. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{targetId}/follow")
    @ResponseStatus(HttpStatus.CREATED)
    public void follow(@AuthenticationPrincipal Long userId, @PathVariable Long targetId) {
        followService.follow(userId, targetId);
    }

    @DeleteMapping("/{targetId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@AuthenticationPrincipal Long userId, @PathVariable Long targetId) {
        followService.unfollow(userId, targetId);
    }

    @GetMapping("/me/following")
    public List<FollowUserResponse> following(@AuthenticationPrincipal Long userId) {
        return followService.getFollowing(userId);
    }

    @GetMapping("/me/followers")
    public List<FollowUserResponse> followers(@AuthenticationPrincipal Long userId) {
        return followService.getFollowers(userId);
    }

    @GetMapping("/{targetId}/follow-stats")
    public FollowStatsResponse stats(@AuthenticationPrincipal Long userId, @PathVariable Long targetId) {
        return followService.getStats(userId, targetId);
    }
}
