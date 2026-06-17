package com.juhkang.apptoon.domain.admin.dto;

import com.juhkang.apptoon.domain.series.AgeRating;

import jakarta.validation.constraints.NotNull;

public record AgeRatingUpdateRequest(
        @NotNull AgeRating ageRating
) {
}
