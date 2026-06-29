package com.juhkang.artiv.domain.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);
    long countByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, Long targetId, ReportStatus status);
    long countByTargetTypeAndTargetId(ReportTargetType targetType, Long targetId);

    /** 관리자 큐 — 상태·대상종류·사유 필터(모두 null이면 전체). */
    @Query(value = "select r from Report r where (:status is null or r.status = :status) "
            + "and (:targetType is null or r.targetType = :targetType) "
            + "and (:reason is null or r.reason = :reason) order by r.id desc",
            countQuery = "select count(r) from Report r where (:status is null or r.status = :status) "
                    + "and (:targetType is null or r.targetType = :targetType) "
                    + "and (:reason is null or r.reason = :reason)")
    Page<Report> findForAdmin(@Param("status") ReportStatus status, @Param("targetType") ReportTargetType targetType,
                              @Param("reason") ReportReason reason, Pageable pageable);
}
