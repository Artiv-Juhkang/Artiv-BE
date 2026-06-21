package com.juhkang.apptoon.domain.user;

import java.time.Instant;

import com.juhkang.apptoon.global.entity.BaseEntity;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 작가 전환 신청. 독자가 사유와 함께 신청 → 관리자가 승인(역할 부여)/거부. */
@Entity
@Table(name = "creator_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreatorRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CreatorRequestStatus status;

    @Column(name = "request_reason", nullable = false, length = 1000)
    private String requestReason;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    private CreatorRequest(Long userId, String requestReason) {
        this.userId = userId;
        this.requestReason = requestReason;
        this.status = CreatorRequestStatus.PENDING;
    }

    public static CreatorRequest create(Long userId, String requestReason) {
        return new CreatorRequest(userId, requestReason);
    }

    public boolean isPending() {
        return status == CreatorRequestStatus.PENDING;
    }

    public void approve(Long adminId, String adminNote) {
        review(CreatorRequestStatus.APPROVED, adminId, adminNote);
    }

    public void reject(Long adminId, String adminNote) {
        review(CreatorRequestStatus.REJECTED, adminId, adminNote);
    }

    private void review(CreatorRequestStatus next, Long adminId, String adminNote) {
        if (!isPending()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 이미 처리된 신청
        }
        this.status = next;
        this.reviewedBy = adminId;
        this.adminNote = adminNote;
        this.reviewedAt = Instant.now();
    }
}
