package com.juhkang.artiv.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatorRequestSubmitRequest(
        @NotBlank @Size(max = 1000) String reason
) {
}
