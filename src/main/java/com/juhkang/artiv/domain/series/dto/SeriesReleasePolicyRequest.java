package com.juhkang.artiv.domain.series.dto;

import com.juhkang.artiv.domain.series.ReleasePolicy;

import jakarta.validation.constraints.NotNull;

/** 작가 공개정책 변경. WAIT_FREE면 waitFreeDays 필수(>0), FREE_ALL이면 무시. 상세 검증은 엔티티 불변식. */
public record SeriesReleasePolicyRequest(
        @NotNull ReleasePolicy mode,
        Integer waitFreeDays
) {
}
