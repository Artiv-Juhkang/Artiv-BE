package com.juhkang.apptoon.domain.notification;

import java.time.Instant;

import com.juhkang.apptoon.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인앱 알림 한 건. 폴리모픽 타겟(라우팅) + 비정규화 렌더필드(목록 조회 시 조인 0).
 * recipientId는 비연관 Long(대량 fan-out 시 프록시 비용 회피).
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 20)
    private NotificationTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "dedup_key", length = 200)
    private String dedupKey;

    private Notification(Long recipientId, NotificationType type, NotificationTargetType targetType,
                         Long targetId, Long actorId, String title, String message, String dedupKey) {
        this.recipientId = recipientId;
        this.type = type;
        this.targetType = targetType;
        this.targetId = targetId;
        this.actorId = actorId;
        this.title = title;
        this.message = message;
        this.dedupKey = dedupKey;
    }

    public static Notification create(Long recipientId, NotificationType type, NotificationTargetType targetType,
                                      Long targetId, Long actorId, String title, String message, String dedupKey) {
        return new Notification(recipientId, type, targetType, targetId, actorId, title, message, dedupKey);
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public boolean isRecipient(Long userId) {
        return recipientId.equals(userId);
    }
}
