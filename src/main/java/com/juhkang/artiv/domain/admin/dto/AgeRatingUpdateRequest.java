package com.juhkang.artiv.domain.admin.dto;

import com.juhkang.artiv.domain.series.AgeRating;

import jakarta.validation.constraints.NotNull;

public record AgeRatingUpdateRequest(
        @NotNull AgeRating ageRating
) {
}
