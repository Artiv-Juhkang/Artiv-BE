package com.juhkang.apptoon.domain.series.dto;

import com.juhkang.apptoon.domain.series.ReleasePolicy;

public record SeriesReleasePolicyResponse(
        ReleasePolicy mode,
        Integer waitFreeDays
) {
}
