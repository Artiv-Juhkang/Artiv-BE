package com.juhkang.artiv.domain.follow;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.domain.follow.dto.FollowStatsResponse;
import com.juhkang.artiv.domain.follow.dto.FollowUserResponse;
import com.juhkang.artiv.domain.notification.NotificationService;
import com.juhkang.artiv.domain.notification.NotificationTargetType;
import com.juhkang.artiv.domain.notification.NotificationType;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;
import com.juhkang.artiv.global.storage.ImageStorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;
    private final NotificationService notificationService;

    @Transactional
    public void follow(Long followerId, Long targetId) {
        if (followerId.equals(targetId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 자기 자신 팔로우 불가
        }
        if (!userRepository.existsById(targetId)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, targetId)) {
            return; // 멱등 — 재팔로우는 알림 없음
        }
        followRepository.save(Follow.create(followerId, targetId));
        notificationService.fanOut(List.of(targetId), NotificationType.FOLLOWED, NotificationTargetType.USER,
                followerId, followerId, "새 팔로워", "회원님을 팔로우하기 시작했어요.",
                rid -> "FOLLOW:" + followerId);
    }

    @Transactional
    public void unfollow(Long followerId, Long targetId) {
        followRepository.deleteByFollowerIdAndFollowingId(followerId, targetId);
    }

    public List<FollowUserResponse> getFollowing(Long userId) {
        return followRepository.findFollowingUsers(userId).stream().map(this::toUser).toList();
    }

    public List<FollowUserResponse> getFollowers(Long userId) {
        return followRepository.findFollowerUsers(userId).stream().map(this::toUser).toList();
    }

    public FollowStatsResponse getStats(Long viewerId, Long targetId) {
        return new FollowStatsResponse(
                followRepository.countByFollowingId(targetId),
                followRepository.countByFollowerId(targetId),
                followRepository.existsByFollowerIdAndFollowingId(viewerId, targetId));
    }

    private FollowUserResponse toUser(User u) {
        String avatarUrl = u.getAvatarKey() != null ? imageStorageService.urlFor(u.getAvatarKey()) : null;
        return new FollowUserResponse(u.getId(), u.getNickname(), avatarUrl);
    }
}
