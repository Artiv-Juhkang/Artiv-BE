package com.juhkang.artiv.domain.chat.dto;

import java.time.Instant;

import com.juhkang.artiv.domain.chat.Message;

/** 메시지 행 — senderNickname은 users 배치 조인으로 서비스가 채운다. */
public record MessageResponse(
        Long id,
        Long senderId,
        String senderNickname,
        String content,
        Instant createdAt
) {

    public static MessageResponse of(Message m, String senderNickname) {
        return new MessageResponse(m.getId(), m.getSenderId(), senderNickname, m.getContent(), m.getCreatedAt());
    }
}
