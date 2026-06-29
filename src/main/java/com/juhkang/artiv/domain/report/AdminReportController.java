package com.juhkang.artiv.domain.report;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.artiv.domain.report.dto.ReportAdminDetailResponse;
import com.juhkang.artiv.domain.report.dto.ReportAdminResponse;
import com.juhkang.artiv.domain.report.dto.ReportResolveRequest;
import com.juhkang.artiv.global.dto.PageResponse;

import lombok.RequiredArgsConstructor;

/** 관리자 신고 관리 — 큐 조회(필터)·상세(대상 내용)·처리(액션 선택)·기각. */
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping
    public PageResponse<ReportAdminResponse> list(@RequestParam(required = false) ReportStatus status,
                                                  @RequestParam(required = false) ReportTargetType targetType,
                                                  @RequestParam(required = false) ReportReason reason,
                                                  @PageableDefault(size = 30) Pageable pageable) {
        return reportService.getForAdmin(status, targetType, reason, pageable);
    }

    @GetMapping("/{reportId}")
    public ReportAdminDetailResponse detail(@PathVariable Long reportId) {
        return reportService.getDetail(reportId);
    }

    @PatchMapping("/{reportId}/resolve")
    public ReportAdminDetailResponse resolve(@AuthenticationPrincipal Long adminId,
                                             @PathVariable Long reportId,
                                             @RequestBody(required = false) ReportResolveRequest request) {
        return reportService.resolve(adminId, reportId, request != null ? request.action() : null);
    }

    @PatchMapping("/{reportId}/dismiss")
    public ReportAdminDetailResponse dismiss(@AuthenticationPrincipal Long adminId, @PathVariable Long reportId) {
        return reportService.dismiss(adminId, reportId);
    }
}
