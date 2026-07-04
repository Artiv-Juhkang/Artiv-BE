package com.juhkang.artiv.domain.community.dto;

import java.time.Instant;
import java.util.List;

/** 게시글 댓글 — replies로 1-depth 대댓글 동봉. */
public record PostCommentResponse(
        Long id,
        Long authorId,
        String authorNickname,
        String content,
        int likeCount,
        boolean liked,
        int dislikeCount,
        boolean disliked,
        Instant createdAt,
        List<PostCommentResponse> replies
) {
}
