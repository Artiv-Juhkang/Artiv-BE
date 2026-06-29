package com.juhkang.artiv.domain.series.dto;

import java.time.DayOfWeek;
import java.util.Set;

import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.ContentType;
import com.juhkang.artiv.domain.series.Genre;
import com.juhkang.artiv.domain.series.SeriesStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SeriesCreateRequest(
        @NotBlank String title,
        String description,
        @NotNull AgeRating ageRating,
        @NotNull SeriesStatus status,
        ContentType contentType,           // null이면 WEBTOON
        Set<DayOfWeek> publishDays,        // 웹툰 전용(연재요일). 비웹툰은 생략 가능
        Boolean adultOnly,
        Genre genre,
        Set<String> tags
) {
}
