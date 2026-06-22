package com.juhkang.apptoon.domain.notification;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.apptoon.domain.notification.dto.NotificationResponse;
import com.juhkang.apptoon.domain.notification.dto.UnreadSummaryResponse;
import com.juhkang.apptoon.global.dto.PageResponse;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 인앱 알림 저장소. fan-out으로 쌓고, 사용자가 나중에 조회·읽음 처리한다.
 * 푸시(FCM)는 이 위 후처리 — 여기서는 외부 의존 0(폴링).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * 수신자들에게 알림을 생성. dedupKeyFn이 null 아닌 키를 주면 멱등(이미 있으면 건너뜀).
     * 발행 도메인은 이 메서드만 호출하고 알림 내부는 모른다.
     */
    @Transactional
    public void fanOut(List<Long> recipientIds, NotificationType type, NotificationTargetType targetType,
                       Long targetId, Long actorId, String title, String message,
                       Function<Long, String> dedupKeyFn) {
        for (Long recipientId : recipientIds) {
            String dedupKey = dedupKeyFn == null ? null : dedupKeyFn.apply(recipientId);
            if (dedupKey != null && notificationRepository.existsByRecipientIdAndDedupKey(recipientId, dedupKey)) {
                continue;
            }
            notificationRepository.save(Notification.create(
                    recipientId, type, targetType, targetId, actorId, title, message, dedupKey));
        }
    }

    public PageResponse<NotificationResponse> getMine(Long userId, NotificationType type, Pageable pageable) {
        return PageResponse.from((type == null
                ? notificationRepository.findByRecipientIdOrderByIdDesc(userId, pageable)
                : notificationRepository.findByRecipientIdAndTypeOrderByIdDesc(userId, type, pageable))
                .map(NotificationResponse::of));
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(userId);
    }

    public UnreadSummaryResponse unreadSummary(Long userId) {
        Map<NotificationType, Long> byType = new EnumMap<>(NotificationType.class);
        long total = 0;
        for (Object[] row : notificationRepository.countUnreadByType(userId)) {
            long count = (Long) row[1];
            byType.put((NotificationType) row[0], count);
            total += count;
        }
        return new UnreadSummaryResponse(total, byType);
    }

    /** 읽음 처리 + 라우팅 정보 반환 — 클라가 한 번에 "확인 처리 후 이동"한다. */
    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        if (!notification.isRecipient(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        notification.markRead();
        return NotificationResponse.of(notification);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, Instant.now());
    }
}
