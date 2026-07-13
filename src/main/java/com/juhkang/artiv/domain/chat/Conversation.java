package com.juhkang.artiv.domain.chat;

import com.juhkang.artiv.global.entity.BaseEntity;

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

/** 대화방 — 비연관 Long 참조(Notification 스타일). 멤버는 conversation_members가 소유. */
@Entity
@Table(name = "conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConversationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConversationStatus status;

    @Column(length = 100)
    private String title;

    @Column(name = "direct_key", length = 50, unique = true)
    private String directKey;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /** 익명방(CH5, 확정 D4=b) — GROUP 전용. 발신자 표기만 마스킹, senderId는 그대로 저장. */
    @Column(nullable = false)
    private boolean anonymous;

    private Conversation(ConversationType type, ConversationStatus status, String title,
                         String directKey, Long createdBy, boolean anonymous) {
        this.type = type;
        this.status = status;
        this.title = title;
        this.directKey = directKey;
        this.createdBy = createdBy;
        this.anonymous = anonymous;
    }

    /** DIRECT 대화 — 친구(상호 팔로우)면 즉시 ACCEPTED, 아니면 PENDING(수신자 수락 대기). */
    public static Conversation direct(Long createdBy, String directKey, boolean mutualFollow) {
        return new Conversation(ConversationType.DIRECT,
                mutualFollow ? ConversationStatus.ACCEPTED : ConversationStatus.PENDING,
                null, directKey, createdBy, false);
    }

    /** GROUP 대화 — 멤버 전원이 이미 친구 검증을 통과했으므로 요청 없이 항상 즉시 ACCEPTED. */
    public static Conversation group(Long createdBy, String title, boolean anonymous) {
        return new Conversation(ConversationType.GROUP, ConversationStatus.ACCEPTED, title, null, createdBy, anonymous);
    }

    public void accept() {
        this.status = ConversationStatus.ACCEPTED;
    }

    public void decline() {
        this.status = ConversationStatus.DECLINED;
    }

    public boolean isRequester(Long userId) {
        return createdBy.equals(userId);
    }
}
