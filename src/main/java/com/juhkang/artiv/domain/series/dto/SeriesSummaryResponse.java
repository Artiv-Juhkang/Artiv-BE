package com.juhkang.artiv.domain.series.dto;

import java.time.Instant;

import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.ContentType;
import com.juhkang.artiv.domain.series.Genre;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesStatus;

public record SeriesSummaryResponse(
        Long id,
        String title,
        String authorNickname,
        AgeRating ageRating,
        SeriesStatus status,
        ContentType contentType,
        Genre genre,
        boolean visible,
        boolean adultOnly,
        String coverUrl,
        // 최신 발행 회차 시각(V26). 프론트가 'UP'(24h 이내 업데이트) 배지 판정에 쓴다. 회차 없으면 null.
        Instant lastPublishedAt
) {

    public static SeriesSummaryResponse of(Series series) {
        return new SeriesSummaryResponse(
                series.getId(),
                series.getTitle(),
                series.getAuthor().getNickname(),
                series.getAgeRating(),
                series.getStatus(),
                series.getContentType(),
                series.getGenre(),
                series.isVisible(),
                series.isAdultOnly(),
                series.getCoverUrl(),
                series.getLastPublishedAt()
        );
    }
}
