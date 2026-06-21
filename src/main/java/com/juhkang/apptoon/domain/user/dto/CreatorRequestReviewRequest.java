package com.juhkang.apptoon.domain.user.dto;

import jakarta.validation.constraints.Size;

/** 관리자 승인/거부 시 메모(선택). */
public record CreatorRequestReviewRequest(
        @Size(max = 1000) String adminNote
) {
}
