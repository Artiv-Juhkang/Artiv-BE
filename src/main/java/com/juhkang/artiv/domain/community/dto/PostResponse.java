package com.juhkang.artiv.domain.community.dto;

import java.time.Instant;

/** 게시글 목록 항목. */
public record PostResponse(
        Long id,
        Long authorId,
        String category,
        String title,
        String authorNickname,
        int likeCount,
        int dislikeCount,
        Instant createdAt
) {
}
