package com.juhkang.apptoon.domain.user.dto;

import java.time.Instant;

import com.juhkang.apptoon.domain.user.CreatorRequest;
import com.juhkang.apptoon.domain.user.CreatorRequestStatus;

/** 본인 작가 전환 신청 상태. */
public record CreatorRequestResponse(
        Long id,
        CreatorRequestStatus status,
        String requestReason,
        String adminNote,
        Instant createdAt,
        Instant reviewedAt
) {

    public static CreatorRequestResponse of(CreatorRequest r) {
        return new CreatorRequestResponse(r.getId(), r.getStatus(), r.getRequestReason(),
                r.getAdminNote(), r.getCreatedAt(), r.getReviewedAt());
    }
}
