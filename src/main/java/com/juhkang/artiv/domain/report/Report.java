package com.juhkang.artiv.domain.report;

import java.time.Instant;

import com.juhkang.artiv.global.entity.BaseEntity;

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

/** 신고 — 폴리모픽 대상(target_type + target_id). 한 신고자가 한 대상을 1번만. */
@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    @Column(length = 1000)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;

    private Report(Long reporterId, ReportTargetType targetType, Long targetId, ReportReason reason, String detail) {
        this.reporterId = reporterId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
        this.status = ReportStatus.PENDING;
    }

    public static Report create(Long reporterId, ReportTargetType targetType, Long targetId,
                                ReportReason reason, String detail) {
        return new Report(reporterId, targetType, targetId, reason, detail);
    }

    public void resolve(Long adminId) {
        handle(ReportStatus.RESOLVED, adminId);
    }

    public void dismiss(Long adminId) {
        handle(ReportStatus.DISMISSED, adminId);
    }

    private void handle(ReportStatus next, Long adminId) {
        this.status = next;
        this.handledBy = adminId;
        this.handledAt = Instant.now();
    }
}
