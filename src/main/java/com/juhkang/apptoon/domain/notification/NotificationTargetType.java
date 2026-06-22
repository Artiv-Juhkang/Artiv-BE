package com.juhkang.apptoon.domain.notification;

/** 알림 클릭 시 라우팅 대상 분류. (targetType, targetId)가 클라 딥링크 신호. */
public enum NotificationTargetType {
    SERIES, EPISODE, INQUIRY, POST, COMMENT, USER
}
