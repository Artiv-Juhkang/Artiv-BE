package com.juhkang.apptoon.domain.series.dto;

import java.time.DayOfWeek;
import java.util.Set;

import com.juhkang.apptoon.domain.series.AgeRating;
import com.juhkang.apptoon.domain.series.SeriesStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record SeriesCreateRequest(
        @NotBlank String title,
        String description,
        @NotNull AgeRating ageRating,
        @NotNull SeriesStatus status,
        @NotEmpty Set<DayOfWeek> publishDays,
        Boolean adultOnly
) {
}
