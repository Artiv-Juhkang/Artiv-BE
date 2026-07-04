package com.juhkang.artiv.domain.community.dto;

import java.time.Instant;
import java.util.List;

import com.juhkang.artiv.domain.community.PostCategory;

/** 게시글 상세. */
public record PostDetailResponse(
        Long id,
        Long authorId,
        PostCategory category,
        String title,
        String content,
        String authorNickname,
        int likeCount,
        boolean liked,
        List<PostImageResponse> images,
        Instant createdAt
) {
}
