package com.juhkang.apptoon.domain.personalization;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.juhkang.apptoon.global.dto.SeriesMaxNo;

public interface ReadLogRepository extends JpaRepository<ReadLog, Long> {

    boolean existsByUserIdAndEpisodeId(Long userId, Long episodeId);

    /** 열람한 작품 목록(최근 열람순) — 작품별 마지막 본 회차·열람시각. 비공개(visible=false) 제외. */
    @Query(value = "select rl.episode.series.id as seriesId, max(rl.episode.episodeNo) as maxNo, "
            + "max(rl.createdAt) as lastReadAt from ReadLog rl "
            + "where rl.user.id = :userId and rl.episode.series.visible = true "
            + "group by rl.episode.series.id "
            + "order by max(rl.createdAt) desc, rl.episode.series.id desc",   // 2차 키(동률 시 페이지 경계 안정)
            countQuery = "select count(distinct rl.episode.series.id) from ReadLog rl "
                    + "where rl.user.id = :userId and rl.episode.series.visible = true")
    Page<ReadSeriesRow> findReadSeries(@Param("userId") Long userId, Pageable pageable);

    @Query("select rl.episode.series.id as seriesId, max(rl.episode.episodeNo) as maxNo from ReadLog rl "
            + "where rl.user.id = :userId and rl.episode.series.id in :seriesIds "
            + "group by rl.episode.series.id")
    List<SeriesMaxNo> findMaxReadEpisodeNo(@Param("userId") Long userId,
                                           @Param("seriesIds") Collection<Long> seriesIds);
}
