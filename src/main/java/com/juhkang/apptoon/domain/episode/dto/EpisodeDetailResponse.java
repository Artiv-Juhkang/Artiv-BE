package com.juhkang.apptoon.domain.episode.dto;

import java.time.Instant;
import java.util.List;

import com.juhkang.apptoon.domain.episode.Episode;
import com.juhkang.apptoon.domain.episode.EpisodeImage;
import com.juhkang.apptoon.domain.episode.EpisodeStatus;

public record EpisodeDetailResponse(
        int episodeNo,
        String title,
        EpisodeStatus status,
        Instant publishAt,
        List<EpisodeImageResponse> images,
        long likeCount,
        boolean liked
) {

    public static EpisodeDetailResponse of(Episode episode, List<EpisodeImage> images, long likeCount, boolean liked) {
        return new EpisodeDetailResponse(
                episode.getEpisodeNo(),
                episode.getTitle(),
                episode.getStatus(),
                episode.getPublishAt(),
                images.stream().map(EpisodeImageResponse::of).toList(),
                likeCount,
                liked
        );
    }
}
