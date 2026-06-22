package com.juhkang.apptoon.global.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 서버에서 정렬이 고정된 목록(최신순 등)용 — 클라이언트 {@code ?sort=} 파라미터를 버리고 page·size만 취한다.
 * 임의 정렬 속성(예: 존재하지 않는 컬럼, group-by 프로젝션 비호환)으로 인한 500을 막는다.
 */
public final class Pageables {

    private Pageables() {
    }

    public static Pageable pageOnly(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }
}
