package com.juhkang.artiv.domain.series.dto;

import com.juhkang.artiv.domain.series.ReleasePolicy;

public record SeriesReleasePolicyResponse(
        ReleasePolicy mode,
        Integer waitFreeDays
) {
}
