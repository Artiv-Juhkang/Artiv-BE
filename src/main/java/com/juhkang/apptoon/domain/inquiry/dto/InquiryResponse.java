package com.juhkang.apptoon.domain.inquiry.dto;

import java.time.Instant;

import com.juhkang.apptoon.domain.inquiry.Inquiry;
import com.juhkang.apptoon.domain.inquiry.InquiryStatus;
import com.juhkang.apptoon.domain.inquiry.InquiryType;

/** 내 문의 목록 항목. */
public record InquiryResponse(
        Long id,
        InquiryType type,
        String title,
        InquiryStatus status,
        Instant createdAt
) {

    public static InquiryResponse of(Inquiry inquiry) {
        return new InquiryResponse(inquiry.getId(), inquiry.getType(), inquiry.getTitle(),
                inquiry.getStatus(), inquiry.getCreatedAt());
    }
}
