package com.juhkang.apptoon.domain.community.dto;

import java.time.Instant;
import java.util.List;

import com.juhkang.apptoon.domain.community.PostCategory;

/** 게시글 상세. */
public record PostDetailResponse(
        Long id,
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
