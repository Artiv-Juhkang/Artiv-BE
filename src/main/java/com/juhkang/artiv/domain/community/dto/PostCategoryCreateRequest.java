package com.juhkang.artiv.domain.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostCategoryCreateRequest(
        @NotBlank @Size(max = 20) String name
) {
}
