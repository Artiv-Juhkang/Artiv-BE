package com.juhkang.apptoon.domain.inquiry.dto;

import java.time.Instant;
import java.util.List;

import com.juhkang.apptoon.domain.inquiry.Inquiry;
import com.juhkang.apptoon.domain.inquiry.InquiryStatus;
import com.juhkang.apptoon.domain.inquiry.InquiryType;

/** 내 문의 상세 — 내용·답변·첨부 이미지. */
public record InquiryDetailResponse(
        Long id,
        InquiryType type,
        String title,
        String content,
        String answer,
        InquiryStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<InquiryImageResponse> images
) {

    public static InquiryDetailResponse of(Inquiry inquiry, List<InquiryImageResponse> images) {
        return new InquiryDetailResponse(inquiry.getId(), inquiry.getType(), inquiry.getTitle(),
                inquiry.getContent(), inquiry.getAnswer(), inquiry.getStatus(),
                inquiry.getCreatedAt(), inquiry.getUpdatedAt(), images);
    }
}
