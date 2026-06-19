package com.juhkang.apptoon.domain.series.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Set;

import com.juhkang.apptoon.domain.series.AgeRating;
import com.juhkang.apptoon.domain.series.Series;
import com.juhkang.apptoon.domain.series.SeriesStatus;

public record SeriesResponse(
        Long id,
        String title,
        String description,
        String authorNickname,
        AgeRating ageRating,
        SeriesStatus status,
        Set<DayOfWeek> publishDays,
        boolean visible,
        Instant createdAt
) {

    public static SeriesResponse of(Series series) {
        return new SeriesResponse(
                series.getId(),
                series.getTitle(),
                series.getDescription(),
                series.getAuthor().getNickname(),
                series.getAgeRating(),
                series.getStatus(),
                series.getPublishDays(),
                series.isVisible(),
                series.getCreatedAt()
        );
    }
}
