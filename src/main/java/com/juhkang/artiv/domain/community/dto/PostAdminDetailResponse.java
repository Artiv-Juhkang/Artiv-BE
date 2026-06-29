package com.juhkang.artiv.domain.community.dto;

import java.time.Instant;
import java.util.List;

import com.juhkang.artiv.domain.community.PostCategory;

/** 관리자 게시글 상세 — 내용·이미지·블라인드 이력. */
public record PostAdminDetailResponse(
        Long id,
        PostCategory category,
        String title,
        String content,
        String authorNickname,
        int likeCount,
        boolean blinded,
        Instant blindedAt,
        List<PostImageResponse> images,
        Instant createdAt
) {
}
