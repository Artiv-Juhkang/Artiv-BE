package com.juhkang.apptoon.domain.notification.dto;

import java.util.Map;

import com.juhkang.apptoon.domain.notification.NotificationType;

/** 미읽음 종류별 집계 — 모바일 "전체/[종류별]" 분류 탭의 배지. */
public record UnreadSummaryResponse(long total, Map<NotificationType, Long> byType) {
}
