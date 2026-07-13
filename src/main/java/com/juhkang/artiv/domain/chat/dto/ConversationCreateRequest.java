package com.juhkang.artiv.domain.chat.dto;

import java.util.List;

import com.juhkang.artiv.domain.chat.ConversationType;

/**
 * 대화 생성 — DIRECT: targetUserId 필수. GROUP: title+memberIds(친구 2명 이상) 필수, anonymous
 * 선택(CH5). DIRECT 요청은 이 필드를 아예 안 보내므로 primitive boolean이면 역직렬화가
 * 깨진다(레코드 생성자가 누락된 원시값을 못 채움) — Boolean으로 두고 null=false로 다룬다.
 */
public record ConversationCreateRequest(
        ConversationType type,
        Long targetUserId,
        String title,
        List<Long> memberIds,
        Boolean anonymous
) {
}
