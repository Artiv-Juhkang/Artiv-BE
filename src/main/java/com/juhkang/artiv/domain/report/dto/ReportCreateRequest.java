package com.juhkang.artiv.domain.report.dto;

import com.juhkang.artiv.domain.report.ReportReason;
import com.juhkang.artiv.domain.report.ReportTargetType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportCreateRequest(
        @NotNull ReportTargetType targetType,
        @NotNull Long targetId,
        @NotNull ReportReason reason,
        @Size(max = 1000) String detail
) {
}
