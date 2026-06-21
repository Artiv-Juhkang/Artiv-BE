package com.juhkang.apptoon.domain.community.dto;

import java.time.Instant;
import java.util.List;

/** 게시글 댓글 — replies로 1-depth 대댓글 동봉. */
public record PostCommentResponse(
        Long id,
        String authorNickname,
        String content,
        Instant createdAt,
        List<PostCommentResponse> replies
) {
}
