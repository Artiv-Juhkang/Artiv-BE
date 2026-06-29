package com.juhkang.artiv.domain.inquiry;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.juhkang.artiv.domain.auth.AuthSupport;
import com.juhkang.artiv.domain.inquiry.dto.InquiryDetailResponse;
import com.juhkang.artiv.domain.inquiry.dto.InquiryResponse;
import com.juhkang.artiv.global.dto.IdResponse;
import com.juhkang.artiv.global.dto.PageResponse;

import lombok.RequiredArgsConstructor;

/** 내 문의 — 모든 인증 사용자(독자/작가/관리자)가 제출·조회·삭제한다. */
@RestController
@RequestMapping("/api/me/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse create(@AuthenticationPrincipal Long userId,
                             @RequestParam InquiryType type,
                             @RequestParam String title,
                             @RequestParam String content,
                             @RequestPart(name = "images", required = false) List<MultipartFile> images) {
        return new IdResponse(inquiryService.create(userId, type, title, content, images));
    }

    @GetMapping
    public PageResponse<InquiryResponse> mine(@AuthenticationPrincipal Long userId,
                                              @PageableDefault(size = 20) Pageable pageable) {
        return inquiryService.getMine(userId, pageable);
    }

    @GetMapping("/{inquiryId}")
    public InquiryDetailResponse detail(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long inquiryId) {
        return inquiryService.getMineDetail(userId, inquiryId);
    }

    @DeleteMapping("/{inquiryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId,
                       @PathVariable Long inquiryId,
                       Authentication authentication) {
        inquiryService.deleteMine(userId, AuthSupport.isAdmin(authentication), inquiryId);
    }
}
