package com.juhkang.artiv.domain.notification;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientIdOrderByIdDesc(Long recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndTypeOrderByIdDesc(Long recipientId, NotificationType type, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    boolean existsByRecipientIdAndDedupKey(Long recipientId, String dedupKey);

    /** 미읽음을 종류별로 집계. (type, count) — 모바일 종류별 배지용. */
    @Query("""
            select n.type, count(n)
            from Notification n
            where n.recipientId = :recipientId and n.readAt is null
            group by n.type
            """)
    List<Object[]> countUnreadByType(@Param("recipientId") Long recipientId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientId = :recipientId and n.readAt is null")
    void markAllRead(@Param("recipientId") Long recipientId, @Param("now") Instant now);
}
