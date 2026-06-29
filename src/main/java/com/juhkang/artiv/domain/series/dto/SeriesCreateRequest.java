package com.juhkang.artiv.domain.series.dto;

import java.time.DayOfWeek;
import java.util.Set;

import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.Genre;
import com.juhkang.artiv.domain.series.SeriesStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record SeriesCreateRequest(
        @NotBlank String title,
        String description,
        @NotNull AgeRating ageRating,
        @NotNull SeriesStatus status,
        @NotEmpty Set<DayOfWeek> publishDays,
        Boolean adultOnly,
        Genre genre,
        Set<String> tags
) {
}
