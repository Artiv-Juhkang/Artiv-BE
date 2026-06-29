package com.juhkang.artiv.domain.episode;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeLikeRepository extends JpaRepository<EpisodeLike, Long> {

    boolean existsByUserIdAndEpisodeId(Long userId, Long episodeId);

    long deleteByUserIdAndEpisodeId(Long userId, Long episodeId);

    long countByEpisodeId(Long episodeId);
}
