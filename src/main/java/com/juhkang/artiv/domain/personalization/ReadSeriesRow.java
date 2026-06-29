package com.juhkang.artiv.domain.personalization;

import java.time.Instant;

/** 열람 이력 집계 프로젝션 — 작품별 (마지막 본 회차번호, 마지막 열람 시각). */
public interface ReadSeriesRow {
    Long getSeriesId();

    Integer getMaxNo();

    Instant getLastReadAt();
}
