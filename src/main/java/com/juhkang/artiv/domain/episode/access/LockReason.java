package com.juhkang.artiv.domain.episode.access;

/** 회차 잠금 사유. 0단계는 NONE/WAIT만. 미래: PAID·MEMBERSHIP·ENTITLEMENT 추가 자리(값 미정의=YAGNI). */
public enum LockReason {
    NONE,   // 접근 가능
    WAIT    // 기다리면무료 — 무료전환 전(freeAt까지 잠김)
}
