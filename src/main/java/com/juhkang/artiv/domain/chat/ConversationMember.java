package com.juhkang.artiv.domain.chat;

import com.juhkang.artiv.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 대화 참여자 + high-water-mark 읽음 포인터. */
@Entity
@Table(name = "conversation_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationMember extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 10)
    private String role; // 골격만(GROUP 방장 등 후속)

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    private ConversationMember(Long conversationId, Long userId) {
        this.conversationId = conversationId;
        this.userId = userId;
    }

    public static ConversationMember create(Long conversationId, Long userId) {
        return new ConversationMember(conversationId, userId);
    }

    /** 읽음 포인터 전진(후퇴 방지 — 클라 재전송/역순 패치에도 최댓값 유지). */
    public void readUpTo(Long messageId) {
        if (messageId == null) return;
        if (this.lastReadMessageId == null || messageId > this.lastReadMessageId) {
            this.lastReadMessageId = messageId;
        }
    }
}
