package com.juhkang.apptoon.domain.inquiry.dto;

import com.juhkang.apptoon.domain.inquiry.InquiryStatus;

import jakarta.validation.constraints.NotNull;

public record InquiryStatusUpdateRequest(
        @NotNull InquiryStatus status
) {
}
