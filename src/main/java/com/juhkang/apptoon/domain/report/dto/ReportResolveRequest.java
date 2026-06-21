package com.juhkang.apptoon.domain.report.dto;

import com.juhkang.apptoon.domain.report.ReportResolveAction;

/** 신고 처리 요청 — action 없으면 NONE(처리만). */
public record ReportResolveRequest(
        ReportResolveAction action
) {
}
