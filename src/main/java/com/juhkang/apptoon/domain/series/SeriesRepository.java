package com.juhkang.apptoon.domain.series;

import java.time.DayOfWeek;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeriesRepository extends JpaRepository<Series, Long> {

    @Query(value = "select s from Series s join fetch s.author "
            + "where (:day is null or :day member of s.publishDays) "
            + "and (:ageRating is null or s.ageRating = :ageRating)",
            countQuery = "select count(s) from Series s "
                    + "where (:day is null or :day member of s.publishDays) "
                    + "and (:ageRating is null or s.ageRating = :ageRating)")
    Page<Series> search(@Param("day") DayOfWeek day, @Param("ageRating") AgeRating ageRating, Pageable pageable);
}
