package com.juhkang.artiv.domain.episode.dto;

import com.juhkang.artiv.domain.episode.EpisodeImage;
import com.juhkang.artiv.domain.episode.MediaKind;

public record EpisodeImageResponse(
        int sortOrder,
        String url,
        MediaKind mediaKind,
        String mimeType,
        Integer width,
        Integer height,
        Long durationMs
) {

    public static EpisodeImageResponse of(EpisodeImage image, String url) {
        return new EpisodeImageResponse(image.getSortOrder(), url, image.getMediaKind(),
                image.getMimeType(), image.getWidth(), image.getHeight(), image.getDurationMs());
    }
}
