package com.juhkang.artiv.domain.series;

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
            + "and (:genre is null or s.genre = :genre) "
            + "and (:keyword is null or lower(s.title) like lower(concat('%', cast(:keyword as string), '%'))) "
            + "and (:adultOnly is null or s.adultOnly = :adultOnly) "
            + "and (:tag is null or :tag member of s.tags)",
            countQuery = "select count(s) from Series s "
                    + "where s.visible = true "
                    + "and (:day is null or :day member of s.publishDays) "
                    + "and (:ageRating is null or s.ageRating = :ageRating) "
                    + "and (:genre is null or s.genre = :genre) "
                    + "and (:keyword is null or lower(s.title) like lower(concat('%', cast(:keyword as string), '%'))) "
                    + "and (:adultOnly is null or s.adultOnly = :adultOnly) "
                    + "and (:tag is null or :tag member of s.tags)")
    Page<Series> search(@Param("day") DayOfWeek day, @Param("ageRating") AgeRating ageRating,
                         @Param("genre") Genre genre, @Param("keyword") String keyword,
                         @Param("adultOnly") Boolean adultOnly, @Param("tag") String tag, Pageable pageable);

    @Query("select s from Series s join fetch s.author where s.author.id = :authorId order by s.id desc")
    List<Series> findByAuthorId(@Param("authorId") Long authorId);

    /** 관리자용: visible 무관(또는 옵셔널 필터) 전체 작품. 비공개 작품도 관리 목록에 보이게. */
    @Query(value = "select s from Series s join fetch s.author "
            + "where (:visible is null or s.visible = :visible) order by s.id desc",
            countQuery = "select count(s) from Series s where (:visible is null or s.visible = :visible)")
    Page<Series> findForAdmin(@Param("visible") Boolean visible, Pageable pageable);
}
