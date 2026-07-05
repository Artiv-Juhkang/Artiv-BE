package com.juhkang.artiv.domain.chat.dto;

/** 채팅 탭 뱃지 폴링용 — 알림 unread-count와 별도 집계(SSOT: 알림 테이블과 분리). */
public record ChatUnreadCountResponse(long count) {
}
