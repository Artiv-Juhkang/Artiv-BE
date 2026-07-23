package com.juhkang.artiv.domain.personalization.dto;

public record SubscriptionResponse(
        Long seriesId,
        String title,
        String coverUrl,
        int latestEpisodeNo,
        int lastReadEpisodeNo,
        boolean up
) {
}
