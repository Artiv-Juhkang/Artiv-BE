package com.juhkang.artiv.domain.inquiry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    Page<Inquiry> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    /** 관리자 목록 — type/status는 null이면 미적용. 작성자(user) join fetch로 N+1 회피. */
    @Query(value = "select i from Inquiry i join fetch i.user"
            + " where (:type is null or i.type = :type) and (:status is null or i.status = :status)"
            + " order by i.id desc",
            countQuery = "select count(i) from Inquiry i"
                    + " where (:type is null or i.type = :type) and (:status is null or i.status = :status)")
    Page<Inquiry> findForAdmin(@Param("type") InquiryType type,
                               @Param("status") InquiryStatus status,
                               Pageable pageable);
}
