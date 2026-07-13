package com.juhkang.artiv.domain.chat.dto;

import java.util.List;

import com.juhkang.artiv.domain.chat.ConversationType;

/** 대화 생성 — DIRECT: targetUserId 필수. GROUP: title+memberIds(친구 2명 이상) 필수. */
public record ConversationCreateRequest(
        ConversationType type,
        Long targetUserId,
        String title,
        List<Long> memberIds
) {
}
