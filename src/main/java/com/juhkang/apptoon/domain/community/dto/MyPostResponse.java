package com.juhkang.apptoon.domain.community.dto;

import java.time.Instant;

import com.juhkang.apptoon.domain.community.Post;
import com.juhkang.apptoon.domain.community.PostCategory;

/** 내가 쓴 글 항목 — 본인 것이라 작성자 닉네임 생략, 블라인드 상태는 플래그로 노출. */
public record MyPostResponse(
        Long id,
        PostCategory category,
        String title,
        int likeCount,
        boolean blinded,
        Instant createdAt
) {
    public static MyPostResponse of(Post p) {
        return new MyPostResponse(p.getId(), p.getCategory(), p.getTitle(), p.getLikeCount(), p.isBlinded(), p.getCreatedAt());
    }
}
