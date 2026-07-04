package com.juhkang.artiv.domain.community.dto;

import java.time.Instant;

import com.juhkang.artiv.domain.community.PostCategory;

/** 게시글 목록 항목. */
public record PostResponse(
        Long id,
        Long authorId,
        PostCategory category,
        String title,
        String authorNickname,
        int likeCount,
        int dislikeCount,
        Instant createdAt
) {
}
