package com.juhkang.apptoon.domain.report;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.apptoon.domain.report.dto.ReportAdminResponse;
import com.juhkang.apptoon.global.dto.PageResponse;

import lombok.RequiredArgsConstructor;

/** 관리자 신고 관리 — 큐 조회·처리(resolve)·기각(dismiss). */
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping
    public PageResponse<ReportAdminResponse> list(@RequestParam(required = false) ReportStatus status,
                                                  @PageableDefault(size = 30) Pageable pageable) {
        return reportService.getForAdmin(status, pageable);
    }

    @PatchMapping("/{reportId}/resolve")
    public ReportAdminResponse resolve(@AuthenticationPrincipal Long adminId, @PathVariable Long reportId) {
        return reportService.resolve(adminId, reportId);
    }

    @PatchMapping("/{reportId}/dismiss")
    public ReportAdminResponse dismiss(@AuthenticationPrincipal Long adminId, @PathVariable Long reportId) {
        return reportService.dismiss(adminId, reportId);
    }
}
