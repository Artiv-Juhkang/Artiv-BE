package com.juhkang.apptoon.domain.series.dto;

import com.juhkang.apptoon.domain.series.AgeRating;
import com.juhkang.apptoon.domain.series.Genre;
import com.juhkang.apptoon.domain.series.Series;
import com.juhkang.apptoon.domain.series.SeriesStatus;

public record SeriesSummaryResponse(
        Long id,
        String title,
        String authorNickname,
        AgeRating ageRating,
        SeriesStatus status,
        Genre genre,
        boolean visible,
        boolean adultOnly
) {

    public static SeriesSummaryResponse of(Series series) {
        return new SeriesSummaryResponse(
                series.getId(),
                series.getTitle(),
                series.getAuthor().getNickname(),
                series.getAgeRating(),
                series.getStatus(),
                series.getGenre(),
                series.isVisible(),
                series.isAdultOnly()
        );
    }
}
