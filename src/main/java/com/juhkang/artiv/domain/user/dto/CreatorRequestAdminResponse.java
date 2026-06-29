package com.juhkang.artiv.domain.user.dto;

import java.time.Instant;

import com.juhkang.artiv.domain.user.CreatorRequest;
import com.juhkang.artiv.domain.user.CreatorRequestStatus;

/** 관리자 작가 전환 신청 목록·상세 — 신청자 식별 포함. */
public record CreatorRequestAdminResponse(
        Long id,
        Long userId,
        String applicantNickname,
        String applicantEmail,
        CreatorRequestStatus status,
        String requestReason,
        String adminNote,
        Instant createdAt,
        Instant reviewedAt
) {

    public static CreatorRequestAdminResponse of(CreatorRequest r, String nickname, String email) {
        return new CreatorRequestAdminResponse(r.getId(), r.getUserId(), nickname, email, r.getStatus(),
                r.getRequestReason(), r.getAdminNote(), r.getCreatedAt(), r.getReviewedAt());
    }
}
