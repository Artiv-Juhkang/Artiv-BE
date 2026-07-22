package com.juhkang.artiv.domain.follow;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
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
        try {
            followRepository.save(Follow.create(followerId, targetId));
        } catch (DataIntegrityViolationException race) {
            // 동시 팔로우 클릭 경합(uq_follow_pair 유니크 충돌). Postgres가 트랜잭션 전체를 abort시켜
            // 같은 트랜잭션에서 재조회가 불가하므로, 클라이언트가 재시도하면 위 exists 선체크로 멱등
            // 성공하도록 명확한 에러(400)로 위임한다(ChatService.createDirect와 동일 패턴 — F22).
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
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

    /** 친구(상호 팔로우) 목록 — 단체 채팅방 생성 시 초대 후보(CH4). */
    public List<FollowUserResponse> getFriends(Long userId) {
        return followRepository.findMutualFollows(userId).stream().map(this::toUser).toList();
    }

    /**
     * 프로필 비공개 시 팔로워/팔로잉 수를 숨긴다(확3: bio·가입일·팔로우 수만 마스킹).
     * 관계 플래그(isFollowing 등)는 조회자 자신의 상태라 공개 설정과 무관하게 유지한다.
     * 본인이 자기 스탯을 볼 때는 항상 실제 값(마스킹 없음).
     */
    public FollowStatsResponse getStats(Long viewerId, Long targetId) {
        boolean following = followRepository.existsByFollowerIdAndFollowingId(viewerId, targetId);
        boolean followedBy = followRepository.existsByFollowerIdAndFollowingId(targetId, viewerId);
        boolean maskCounts = !viewerId.equals(targetId) && userRepository.findById(targetId)
                .map(u -> !u.isProfilePublic())
                .orElse(false);
        return new FollowStatsResponse(
                maskCounts ? 0 : followRepository.countByFollowingId(targetId),
                maskCounts ? 0 : followRepository.countByFollowerId(targetId),
                following,
                followedBy,
                following && followedBy); // 상호 = 친구(기존 결정: 별도 friendship 테이블 없음)
    }

    private FollowUserResponse toUser(User u) {
        String avatarUrl = u.getAvatarKey() != null ? imageStorageService.urlFor(u.getAvatarKey()) : null;
        return new FollowUserResponse(u.getId(), u.getNickname(), avatarUrl);
    }
}
