package com.juhkang.artiv.domain.inquiry.dto;

import com.juhkang.artiv.domain.inquiry.InquiryStatus;

import jakarta.validation.constraints.NotNull;

public record InquiryStatusUpdateRequest(
        @NotNull InquiryStatus status
) {
}
