package com.juhkang.artiv.domain.notification.dto;

import java.time.Instant;

import com.juhkang.artiv.domain.notification.Notification;
import com.juhkang.artiv.domain.notification.NotificationTargetType;
import com.juhkang.artiv.domain.notification.NotificationType;

/** 알림 한 건. (targetType, targetId)가 클릭 시 라우팅 신호. */
public record NotificationResponse(
        Long id,
        NotificationType type,
        NotificationTargetType targetType,
        Long targetId,
        Long actorId,
        String title,
        String message,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse of(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTargetType(), n.getTargetId(), n.getActorId(),
                n.getTitle(), n.getMessage(), n.isRead(), n.getCreatedAt());
    }
}
