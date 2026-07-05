package com.juhkang.artiv.domain.chat.dto;

/** 메시지 전송 — 텍스트 전용(≤2000자, 검증은 서비스). */
public record MessageCreateRequest(String content) {
}
