package com.juhkang.artiv.domain.chat.dto;

import com.juhkang.artiv.domain.chat.Conversation;
import com.juhkang.artiv.domain.chat.ConversationStatus;
import com.juhkang.artiv.domain.chat.ConversationType;

/** 대화 생성/조회 응답(간단형) — 목록 표시는 ConversationSummaryResponse. */
public record ConversationResponse(
        Long id,
        ConversationType type,
        ConversationStatus status,
        boolean anonymous
) {

    public static ConversationResponse of(Conversation c) {
        return new ConversationResponse(c.getId(), c.getType(), c.getStatus(), c.isAnonymous());
    }
}
