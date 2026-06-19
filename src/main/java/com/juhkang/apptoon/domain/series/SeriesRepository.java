package com.juhkang.apptoon.domain.series;

import java.time.DayOfWeek;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeriesRepository extends JpaRepository<Series, Long> {

    @Query(value = "select s from Series s join fetch s.author "
            + "where s.visible = true "
            + "and (:day is null or :day member of s.publishDays) "
            + "and (:ageRating is null or s.ageRating = :ageRating) "
            + "and (:keyword is null or lower(s.title) like lower(concat('%', cast(:keyword as string), '%'))) "
            + "and (:adultOnly is null or s.adultOnly = :adultOnly)",
            countQuery = "select count(s) from Series s "
                    + "where s.visible = true "
                    + "and (:day is null or :day member of s.publishDays) "
                    + "and (:ageRating is null or s.ageRating = :ageRating) "
                    + "and (:keyword is null or lower(s.title) like lower(concat('%', cast(:keyword as string), '%'))) "
                    + "and (:adultOnly is null or s.adultOnly = :adultOnly)")
    Page<Series> search(@Param("day") DayOfWeek day, @Param("ageRating") AgeRating ageRating,
                         @Param("keyword") String keyword, @Param("adultOnly") Boolean adultOnly, Pageable pageable);

    @Query("select s from Series s join fetch s.author where s.author.id = :authorId order by s.id desc")
    List<Series> findByAuthorId(@Param("authorId") Long authorId);
}
