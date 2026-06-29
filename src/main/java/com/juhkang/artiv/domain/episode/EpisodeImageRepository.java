package com.juhkang.artiv.domain.episode;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeImageRepository extends JpaRepository<EpisodeImage, Long> {

    List<EpisodeImage> findByEpisodeIdOrderBySortOrderAsc(Long episodeId);
}
