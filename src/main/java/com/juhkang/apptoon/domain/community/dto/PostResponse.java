package com.juhkang.apptoon.domain.community.dto;

import java.time.Instant;

import com.juhkang.apptoon.domain.community.PostCategory;

/** 게시글 목록 항목. */
public record PostResponse(
        Long id,
        PostCategory category,
        String title,
        String authorNickname,
        int likeCount,
        Instant createdAt
) {
}
