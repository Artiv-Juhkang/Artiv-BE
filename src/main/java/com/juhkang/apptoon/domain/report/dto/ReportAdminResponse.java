package com.juhkang.apptoon.domain.report.dto;

import java.time.Instant;

import com.juhkang.apptoon.domain.report.Report;
import com.juhkang.apptoon.domain.report.ReportReason;
import com.juhkang.apptoon.domain.report.ReportStatus;
import com.juhkang.apptoon.domain.report.ReportTargetType;

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
