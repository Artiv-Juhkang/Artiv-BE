package com.juhkang.apptoon.domain.episode.access;

import java.time.Instant;

/** 회차 접근 평가 결과. freeAt은 WAIT일 때 무료전환 시각, 그 외 null. 미래 price 등은 필드 증분. */
public record AccessResult(boolean accessible, LockReason lockReason, Instant freeAt) {

    public static AccessResult open() {
        return new AccessResult(true, LockReason.NONE, null);
    }

    public static AccessResult waitLocked(Instant freeAt) {
        return new AccessResult(false, LockReason.WAIT, freeAt);
    }
}
