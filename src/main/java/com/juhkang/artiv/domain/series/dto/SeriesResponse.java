package com.juhkang.artiv.domain.series.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Set;

import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesStatus;

public record SeriesResponse(
        Long id,
        String title,
        String description,
        String authorNickname,
        AgeRating ageRating,
        SeriesStatus status,
        Set<DayOfWeek> publishDays,
        boolean visible,
        boolean adultOnly,
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
                Set.copyOf(series.getPublishDays()), // LAZY 컬렉션을 트랜잭션 안에서 즉시 복사(세션밖 직렬화 방지)
                series.isVisible(),
                series.isAdultOnly(),
                series.getCreatedAt()
        );
    }
}
