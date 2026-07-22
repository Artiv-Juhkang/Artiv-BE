package com.juhkang.artiv.domain.chat.dto;

import java.time.Instant;

import com.juhkang.artiv.domain.chat.ConversationStatus;
import com.juhkang.artiv.domain.chat.ConversationType;

/**
 * 인박스/요청함 행 — 표시명(DIRECT=상대 닉네임)·마지막 메시지 미리보기·미읽음 수를
 * 서버가 파생해 내려준다(FE 분기 최소화 — §2 설계).
 */
public record ConversationSummaryResponse(
        Long id,
        ConversationType type,
        ConversationStatus status,
        boolean anonymous,
        int memberCount,
        String displayName,
        Long partnerId,
        String partnerAvatarUrl,
        String lastMessage,
        Instant lastMessageAt,
        long unreadCount
) {
}
