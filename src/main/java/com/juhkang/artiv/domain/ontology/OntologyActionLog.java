package com.juhkang.artiv.domain.ontology;

import java.time.Instant;

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
 * 액션 감사 로그(append-only). 거부된 시도도 남긴다 — "보내려 했으나 막혔다"가
 * "보내지 않았다"보다 운영에 필요한 정보다.
 *
 * ReadingEvent와 같은 이유로 BaseEntity를 상속하지 않는다(생성 후 불변, occurred_at이 곧 생성 시각).
 * 수신자 개인 id를 담지 않고 recipientCount 정수만 둔다 — V37 주석 참조.
 */
@Entity
@Table(name = "ontology_action_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OntologyActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private ActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false, length = 40)
    private ObjectType objectType;

    @Column(name = "object_id", nullable = false)
    private Long objectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActionResult result;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    private OntologyActionLog(Instant occurredAt, Long actorId, ActionType actionType,
                              ObjectType objectType, Long objectId, ActionResult result, int recipientCount) {
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.actionType = actionType;
        this.objectType = objectType;
        this.objectId = objectId;
        this.result = result;
        this.recipientCount = recipientCount;
    }

    public static OntologyActionLog of(Long actorId, ActionType actionType, Long objectId,
                                       ActionResult result, int recipientCount) {
        return new OntologyActionLog(Instant.now(), actorId, actionType, actionType.getTarget(),
                objectId, result, recipientCount);
    }

    /** 거부 기록 — 수신자 규모는 남기지 않는다(k 미달 케이스에서 그 수 자체가 노출이 된다). */
    public static OntologyActionLog blocked(Long actorId, ActionType actionType, Long objectId,
                                            ActionResult result) {
        return of(actorId, actionType, objectId, result, 0);
    }
}
