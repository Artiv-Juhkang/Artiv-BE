package com.juhkang.artiv.domain.inquiry.dto;

import java.time.Instant;
import java.util.List;

import com.juhkang.artiv.domain.inquiry.Inquiry;
import com.juhkang.artiv.domain.inquiry.InquiryStatus;
import com.juhkang.artiv.domain.inquiry.InquiryType;
import com.juhkang.artiv.domain.user.Role;

/** 관리자 문의 상세 — 발신자 식별정보(닉네임·이메일·역할)와 내용·답변·이미지. */
public record InquiryAdminDetailResponse(
        Long id,
        InquiryType type,
        String title,
        String content,
        String answer,
        InquiryStatus status,
        String authorNickname,
        String authorEmail,
        Role authorRole,
        Instant createdAt,
        Instant updatedAt,
        List<InquiryImageResponse> images
) {

    public static InquiryAdminDetailResponse of(Inquiry inquiry, List<InquiryImageResponse> images) {
        return new InquiryAdminDetailResponse(inquiry.getId(), inquiry.getType(), inquiry.getTitle(),
                inquiry.getContent(), inquiry.getAnswer(), inquiry.getStatus(),
                inquiry.getUser().getNickname(), inquiry.getUser().getEmail(), inquiry.getUser().getRole(),
                inquiry.getCreatedAt(), inquiry.getUpdatedAt(), images);
    }
}
