package com.juhkang.apptoon.domain.user.dto;

import jakarta.validation.constraints.Size;

public record BioUpdateRequest(
        @Size(max = 500) String bio
) {
}
