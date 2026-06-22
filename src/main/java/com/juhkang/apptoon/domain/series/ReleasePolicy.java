package com.juhkang.apptoon.domain.series;

/**
 * 작품 공개정책(수익화 0단계). 결제 없이 작가가 작품 단위로 설정.
 * 미래: PAID_PREVIEW(유료 미리보기)·MEMBERSHIP(멤버십) 상수 추가로 확장(STRING 매핑이라 ordinal 무관).
 */
public enum ReleasePolicy {
    FREE_ALL,   // 전체무료
    WAIT_FREE   // 기다리면무료 — 최신화 시간잠금, N일 후 무료 전환
}
