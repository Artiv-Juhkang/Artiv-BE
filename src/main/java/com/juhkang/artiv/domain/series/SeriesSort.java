package com.juhkang.artiv.domain.series;

import org.springframework.data.domain.Sort;

/**
 * 작품 목록 정렬 옵션. boolean adultOnly 기준 정렬이라 STRING enum 사전순 오작동을 피한다.
 * (ageRating은 STRING 저장이라 사전순 정렬이 연령 강도와 어긋나므로 정렬 키로 노출하지 않는다.)
 */
public enum SeriesSort {

    /** 최신 등록순(기본). */
    LATEST(Sort.by(Sort.Direction.DESC, "id")),

    /** 최근 업데이트순 — 최신 발행 회차 시각(last_published_at) DESC, 회차 없는 작품은 뒤로(NULLS LAST). */
    UPDATED(Sort.by(new Sort.Order(Sort.Direction.DESC, "lastPublishedAt").nullsLast(),
            new Sort.Order(Sort.Direction.DESC, "id"))),

    /** 성인 전용(adultOnly=true) 작품을 먼저. */
    ADULT_FIRST(Sort.by(Sort.Direction.DESC, "adultOnly", "id"));

    private final Sort sort;

    SeriesSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }
}
