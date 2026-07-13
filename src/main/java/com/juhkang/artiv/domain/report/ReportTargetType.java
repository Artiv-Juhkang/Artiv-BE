package com.juhkang.artiv.domain.report;

/** 신고 대상 종류(폴리모픽). MESSAGE는 익명방(CH5) 도입에 맞춰 추가 — 익명도 신고 가능해야 한다. */
public enum ReportTargetType {
    POST, COMMENT, USER, SERIES, EPISODE, MESSAGE
}
