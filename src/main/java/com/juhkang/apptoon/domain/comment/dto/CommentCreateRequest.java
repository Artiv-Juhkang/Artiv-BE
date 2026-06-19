package com.juhkang.apptoon.domain.comment.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentCreateRequest(
        @NotBlank String content
) {
}
