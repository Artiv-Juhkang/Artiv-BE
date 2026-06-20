package com.juhkang.apptoon.domain.inquiry;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.apptoon.domain.inquiry.dto.InquiryAdminDetailResponse;
import com.juhkang.apptoon.domain.inquiry.dto.InquiryAdminResponse;
import com.juhkang.apptoon.domain.inquiry.dto.InquiryAnswerRequest;
import com.juhkang.apptoon.domain.inquiry.dto.InquiryStatusUpdateRequest;
import com.juhkang.apptoon.global.dto.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 관리자 문의 관리 — 전체 목록(필터)·상세·답변·상태·삭제. ADMIN 전용. */
@RestController
@RequestMapping("/api/admin/inquiries")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminInquiryController {

    private final InquiryService inquiryService;

    @GetMapping
    public PageResponse<InquiryAdminResponse> list(@RequestParam(required = false) InquiryType type,
                                                   @RequestParam(required = false) InquiryStatus status,
                                                   @PageableDefault(size = 20) Pageable pageable) {
        return inquiryService.getForAdmin(type, status, pageable);
    }

    @GetMapping("/{inquiryId}")
    public InquiryAdminDetailResponse detail(@PathVariable Long inquiryId) {
        return inquiryService.getAdminDetail(inquiryId);
    }

    @PatchMapping("/{inquiryId}/answer")
    public InquiryAdminDetailResponse answer(@PathVariable Long inquiryId,
                                             @Valid @RequestBody InquiryAnswerRequest request) {
        return inquiryService.answer(inquiryId, request.answer());
    }

    @PatchMapping("/{inquiryId}/status")
    public InquiryAdminDetailResponse changeStatus(@PathVariable Long inquiryId,
                                                   @Valid @RequestBody InquiryStatusUpdateRequest request) {
        return inquiryService.changeStatus(inquiryId, request.status());
    }

    @DeleteMapping("/{inquiryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long inquiryId) {
        inquiryService.deleteByAdmin(inquiryId);
    }
}
