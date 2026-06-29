package com.juhkang.artiv.domain.report.dto;

import java.time.Instant;

import com.juhkang.artiv.domain.report.Report;
import com.juhkang.artiv.domain.report.ReportReason;
import com.juhkang.artiv.domain.report.ReportStatus;
import com.juhkang.artiv.domain.report.ReportTargetType;

public record ReportAdminResponse(
        Long id,
        String reporterNickname,
        ReportTargetType targetType,
        Long targetId,
        ReportReason reason,
        String detail,
        ReportStatus status,
        Instant createdAt
) {
    public static ReportAdminResponse of(Report r, String reporterNickname) {
        return new ReportAdminResponse(r.getId(), reporterNickname, r.getTargetType(), r.getTargetId(),
                r.getReason(), r.getDetail(), r.getStatus(), r.getCreatedAt());
    }
}
