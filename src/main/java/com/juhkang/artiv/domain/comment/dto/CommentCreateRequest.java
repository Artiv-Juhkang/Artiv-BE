package com.juhkang.artiv.domain.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @NotBlank @Size(max = 1000) String content,
        /** 대댓글이면 부모 댓글 id, 최상위 댓글이면 null. */
        Long parentId
) {
}
