package com.juhkang.apptoon.domain.episode.dto;

import com.juhkang.apptoon.domain.episode.EpisodeImage;

public record EpisodeImageResponse(
        int sortOrder,
        String path,
        int width,
        int height
) {

    public static EpisodeImageResponse of(EpisodeImage image) {
        return new EpisodeImageResponse(image.getSortOrder(), image.getPath(), image.getWidth(), image.getHeight());
    }
}
