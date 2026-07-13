package com.juhkang.artiv.domain.community.dto;

import java.time.Instant;
import java.util.List;

/** 게시글 상세. */
public record PostDetailResponse(
        Long id,
        Long authorId,
        String category,
        String title,
        String content,
        String authorNickname,
        int likeCount,
        boolean liked,
        int dislikeCount,
        boolean disliked,
        List<PostImageResponse> images,
        Instant createdAt
) {
}
