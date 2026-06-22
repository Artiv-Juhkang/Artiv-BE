package com.juhkang.apptoon.domain.notification;

/** 알림 종류. 모바일 "전체/[종류별]" 분류 기준. 새 fan-out을 붙일 때 값 추가. */
public enum NotificationType {
    EPISODE_PUBLISHED,   // 구독 작품 새 회차
    INQUIRY_ANSWERED     // 내 문의 답변
}
