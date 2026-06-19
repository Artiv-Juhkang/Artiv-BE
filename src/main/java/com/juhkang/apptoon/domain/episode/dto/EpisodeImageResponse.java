package com.juhkang.apptoon.domain.episode.dto;

import com.juhkang.apptoon.domain.episode.EpisodeImage;

public record EpisodeImageResponse(
        int sortOrder,
        String url,
        int width,
        int height
) {

    public static EpisodeImageResponse of(EpisodeImage image, String url) {
        return new EpisodeImageResponse(image.getSortOrder(), url, image.getWidth(), image.getHeight());
    }
}
