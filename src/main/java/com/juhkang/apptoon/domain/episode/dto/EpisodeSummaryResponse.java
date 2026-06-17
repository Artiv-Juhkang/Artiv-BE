package com.juhkang.apptoon.domain.episode.dto;

import java.time.Instant;

import com.juhkang.apptoon.domain.episode.Episode;

public record EpisodeSummaryResponse(
        int episodeNo,
        String title,
        Instant publishAt
) {

    public static EpisodeSummaryResponse of(Episode episode) {
        return new EpisodeSummaryResponse(episode.getEpisodeNo(), episode.getTitle(), episode.getPublishAt());
    }
}
