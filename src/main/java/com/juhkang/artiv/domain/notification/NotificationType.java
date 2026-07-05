package com.juhkang.artiv.domain.notification;

/** 알림 종류. 모바일 "전체/[종류별]" 분류 기준. 새 fan-out을 붙일 때 값 추가. */
public enum NotificationType {
    EPISODE_PUBLISHED,   // 구독 작품 새 회차
    INQUIRY_ANSWERED,    // 내 문의 답변
    POST_COMMENT,        // 내 글에 댓글
    COMMENT_REPLY,       // 내 댓글에 답글
    FOLLOWED,            // 새 팔로워
    POST_MENTIONED,      // @닉네임 멘션
    DM_REQUEST           // 새 메시지 요청(비상호 DIRECT 대화 생성)
}
