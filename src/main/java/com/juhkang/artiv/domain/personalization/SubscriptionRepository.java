package com.juhkang.artiv.domain.personalization;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByUserIdAndSeriesId(Long userId, Long seriesId);

    long deleteByUserIdAndSeriesId(Long userId, Long seriesId);

    // fetch join + 페이징: series는 ManyToOne이라 SQL 레벨 limit 안전. count는 fetch 없이 별도.
    @Query(value = "select s from Subscription s join fetch s.series where s.user.id = :userId order by s.id desc",
            countQuery = "select count(s) from Subscription s where s.user.id = :userId")
    Page<Subscription> findByUserIdWithSeries(@Param("userId") Long userId, Pageable pageable);

    @Query("select s.user.id from Subscription s where s.series.id = :seriesId")
    List<Long> findSubscriberIdsBySeriesId(@Param("seriesId") Long seriesId);
}
