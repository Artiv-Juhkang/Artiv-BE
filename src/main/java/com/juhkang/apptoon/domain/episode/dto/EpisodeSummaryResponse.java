package com.juhkang.apptoon.domain.episode.dto;

import java.time.Instant;

import com.juhkang.apptoon.domain.episode.Episode;
import com.juhkang.apptoon.domain.episode.access.AccessResult;

public record EpisodeSummaryResponse(
        int episodeNo,
        String title,
        Instant publishAt,
        boolean locked,
        Instant freeAt
) {

    public static EpisodeSummaryResponse of(Episode episode, AccessResult access) {
        return new EpisodeSummaryResponse(
                episode.getEpisodeNo(),
                episode.getTitle(),
                episode.getPublishAt(),
                !access.accessible(),
                access.freeAt()
        );
    }
}
