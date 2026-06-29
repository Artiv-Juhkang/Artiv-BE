package com.juhkang.artiv.domain.user;

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

import com.juhkang.artiv.domain.user.dto.CreatorRequestAdminResponse;
import com.juhkang.artiv.domain.user.dto.CreatorRequestReviewRequest;
import com.juhkang.artiv.global.dto.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 관리자 작가 전환 신청 관리 — 목록·승인(역할 부여)·거부. */
@RestController
@RequestMapping("/api/admin/creator-requests")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCreatorRequestController {

    private final CreatorRequestService creatorRequestService;

    @GetMapping
    public PageResponse<CreatorRequestAdminResponse> list(
            @RequestParam(required = false) CreatorRequestStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return creatorRequestService.getForAdmin(status, pageable);
    }

    @PatchMapping("/{requestId}/approve")
    public CreatorRequestAdminResponse approve(@AuthenticationPrincipal Long adminId,
                                               @PathVariable Long requestId,
                                               @Valid @RequestBody(required = false) CreatorRequestReviewRequest request) {
        return creatorRequestService.approve(adminId, requestId, request != null ? request.adminNote() : null);
    }

    @PatchMapping("/{requestId}/reject")
    public CreatorRequestAdminResponse reject(@AuthenticationPrincipal Long adminId,
                                              @PathVariable Long requestId,
                                              @Valid @RequestBody(required = false) CreatorRequestReviewRequest request) {
        return creatorRequestService.reject(adminId, requestId, request != null ? request.adminNote() : null);
    }
}
