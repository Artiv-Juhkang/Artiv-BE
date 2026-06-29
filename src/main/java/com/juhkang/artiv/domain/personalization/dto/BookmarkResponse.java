package com.juhkang.artiv.domain.personalization.dto;

import java.time.Instant;

import com.juhkang.artiv.domain.episode.Episode;
import com.juhkang.artiv.domain.personalization.Bookmark;

public record BookmarkResponse(
        Long seriesId,
        String seriesTitle,
        int episodeNo,
        String episodeTitle,
        Instant createdAt
) {

    public static BookmarkResponse of(Bookmark bookmark) {
        Episode episode = bookmark.getEpisode();
        return new BookmarkResponse(
                episode.getSeries().getId(),
                episode.getSeries().getTitle(),
                episode.getEpisodeNo(),
                episode.getTitle(),
                bookmark.getCreatedAt()
        );
    }
}
