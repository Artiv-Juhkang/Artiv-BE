package com.juhkang.artiv.domain.episode.dto;

import java.time.Instant;

import com.juhkang.artiv.domain.episode.Episode;
import com.juhkang.artiv.domain.episode.access.AccessResult;

public record EpisodeSummaryResponse(
        Long id,
        int episodeNo,
        String title,
        Instant publishAt,
        boolean locked,
        Instant freeAt
) {

    public static EpisodeSummaryResponse of(Episode episode, AccessResult access) {
        return new EpisodeSummaryResponse(
                episode.getId(),
                episode.getEpisodeNo(),
                episode.getTitle(),
                episode.getPublishAt(),
                !access.accessible(),
                access.freeAt()
        );
    }
}
