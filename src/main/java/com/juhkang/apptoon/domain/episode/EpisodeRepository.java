package com.juhkang.apptoon.domain.episode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {

    @Query("select coalesce(max(e.episodeNo), 0) from Episode e where e.series.id = :seriesId")
    int findMaxEpisodeNo(@Param("seriesId") Long seriesId);

    Optional<Episode> findBySeriesIdAndEpisodeNo(Long seriesId, int episodeNo);

    Slice<Episode> findBySeriesIdAndStatusOrderByEpisodeNoAsc(Long seriesId, EpisodeStatus status, Pageable pageable);

    List<Episode> findByStatusAndPublishAtLessThanEqual(EpisodeStatus status, Instant time);
}
