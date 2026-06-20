package com.juhkang.apptoon.domain.inquiry.dto;

import java.time.Instant;

import com.juhkang.apptoon.domain.inquiry.Inquiry;
import com.juhkang.apptoon.domain.inquiry.InquiryStatus;
import com.juhkang.apptoon.domain.inquiry.InquiryType;
import com.juhkang.apptoon.domain.user.Role;

/** 관리자 문의 목록 항목 — 발신자(닉네임·역할) 포함. */
public record InquiryAdminResponse(
        Long id,
        InquiryType type,
        String title,
        InquiryStatus status,
        String authorNickname,
        Role authorRole,
        Instant createdAt
) {

    public static InquiryAdminResponse of(Inquiry inquiry) {
        return new InquiryAdminResponse(inquiry.getId(), inquiry.getType(), inquiry.getTitle(),
                inquiry.getStatus(), inquiry.getUser().getNickname(), inquiry.getUser().getRole(),
                inquiry.getCreatedAt());
    }
}
