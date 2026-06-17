package com.juhkang.apptoon.domain.episode.dto;

import com.juhkang.apptoon.domain.episode.EpisodeImage;

public record EpisodeImageResponse(
        int sortOrder,
        String url,
        int width,
        int height
) {

    public static EpisodeImageResponse of(EpisodeImage image) {
        return new EpisodeImageResponse(image.getSortOrder(), "/files/" + image.getPath(), image.getWidth(), image.getHeight());
    }
}
