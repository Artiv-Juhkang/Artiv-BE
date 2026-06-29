package com.juhkang.artiv.domain.community.dto;

import java.time.Instant;

import com.juhkang.artiv.domain.community.PostCategory;

/** 관리자 커뮤니티 목록(블라인드 포함). */
public record PostAdminResponse(
        Long id,
        PostCategory category,
        String title,
        String authorNickname,
        int likeCount,
        boolean blinded,
        Instant createdAt
) {
}
