package com.juhkang.artiv.domain.chat.dto;

import com.juhkang.artiv.domain.chat.ConversationType;

/** 대화 생성 — DIRECT: targetUserId 필수. (GROUP: title+memberIds — CH4에서 처리) */
public record ConversationCreateRequest(
        ConversationType type,
        Long targetUserId
) {
}
