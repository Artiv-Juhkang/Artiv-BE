package com.juhkang.artiv.domain.chat;

/**
 * DM 요청 상태 — 비상호(친구 아님) DIRECT는 PENDING으로 시작해 수신자의 수락/거절을 기다린다.
 * 친구(상호 팔로우)끼리는 생성 즉시 ACCEPTED. DECLINED는 영구(direct_key 유니크가 재요청을 자연 차단
 * — 조용한 거절, 확정 D-확4). GROUP은 항상 ACCEPTED.
 */
public enum ConversationStatus {
    PENDING, ACCEPTED, DECLINED
}
