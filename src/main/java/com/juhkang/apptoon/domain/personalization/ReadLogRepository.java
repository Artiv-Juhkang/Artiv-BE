package com.juhkang.apptoon.domain.personalization;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.juhkang.apptoon.global.dto.SeriesMaxNo;

public interface ReadLogRepository extends JpaRepository<ReadLog, Long> {

    boolean existsByUserIdAndEpisodeId(Long userId, Long episodeId);

    @Query("select rl.episode.series.id as seriesId, max(rl.episode.episodeNo) as maxNo from ReadLog rl "
            + "where rl.user.id = :userId and rl.episode.series.id in :seriesIds "
            + "group by rl.episode.series.id")
    List<SeriesMaxNo> findMaxReadEpisodeNo(@Param("userId") Long userId,
                                           @Param("seriesIds") Collection<Long> seriesIds);
}
