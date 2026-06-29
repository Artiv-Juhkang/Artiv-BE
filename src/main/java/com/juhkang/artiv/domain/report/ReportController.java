package com.juhkang.artiv.domain.report;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.artiv.domain.report.dto.ReportCreateRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 신고 접수 — 모든 인증 사용자가 글/댓글/유저/작품을 신고. */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void report(@AuthenticationPrincipal Long userId, @Valid @RequestBody ReportCreateRequest request) {
        reportService.create(userId, request.targetType(), request.targetId(), request.reason(), request.detail());
    }
}
