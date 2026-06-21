package com.juhkang.apptoon.domain.report.dto;

import java.time.Instant;

import com.juhkang.apptoon.domain.report.Report;
import com.juhkang.apptoon.domain.report.ReportReason;
import com.juhkang.apptoon.domain.report.ReportStatus;
import com.juhkang.apptoon.domain.report.ReportTargetType;

/** 신고 상세 — 신고 정보 + 대상 내용/작성자/관련신고 수(관리자 검토용). */
public record ReportAdminDetailResponse(
        Long id,
        String reporterNickname,
        ReportTargetType targetType,
        Long targetId,
        String targetContent,
        String targetAuthorNickname,
        long relatedReportCount,
        ReportReason reason,
        String detail,
        ReportStatus status,
        Instant createdAt
) {
    public static ReportAdminDetailResponse of(Report r, String reporterNickname, String targetContent,
                                               String targetAuthorNickname, long relatedReportCount) {
        return new ReportAdminDetailResponse(r.getId(), reporterNickname, r.getTargetType(), r.getTargetId(),
                targetContent, targetAuthorNickname, relatedReportCount, r.getReason(), r.getDetail(),
                r.getStatus(), r.getCreatedAt());
    }
}
