package com.juhkang.artiv.domain.chat.dto;

/** 읽음 처리 — 이 메시지 id까지 읽음(high-water-mark, 후퇴 없음). */
public record ReadUpToRequest(Long lastReadMessageId) {
}
