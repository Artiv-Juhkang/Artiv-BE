package com.juhkang.artiv.domain.community.dto;

import java.time.Instant;

/** 관리자 커뮤니티 목록(블라인드 포함). */
public record PostAdminResponse(
        Long id,
        String category,
        String title,
        String authorNickname,
        int likeCount,
        boolean blinded,
        Instant createdAt
) {
}
