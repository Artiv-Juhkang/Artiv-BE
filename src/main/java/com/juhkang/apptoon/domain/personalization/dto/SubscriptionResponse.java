package com.juhkang.apptoon.domain.personalization.dto;

public record SubscriptionResponse(
        Long seriesId,
        String title,
        int latestEpisodeNo,
        int lastReadEpisodeNo,
        boolean up
) {
}
