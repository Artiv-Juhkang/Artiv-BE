package com.juhkang.artiv.domain.admin.dto;

import jakarta.validation.constraints.NotNull;

public record AdultOnlyUpdateRequest(
        @NotNull Boolean adultOnly
) {
}
