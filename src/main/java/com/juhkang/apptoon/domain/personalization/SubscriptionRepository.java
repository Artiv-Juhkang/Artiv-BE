package com.juhkang.apptoon.domain.personalization;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByUserIdAndSeriesId(Long userId, Long seriesId);

    long deleteByUserIdAndSeriesId(Long userId, Long seriesId);

    @Query("select s from Subscription s join fetch s.series where s.user.id = :userId order by s.id desc")
    List<Subscription> findByUserIdWithSeries(@Param("userId") Long userId);
}
