package com.juhkang.artiv.domain.community.dto;

import java.time.Instant;

/** 내가 쓴 댓글·대댓글 항목 — 원글 맥락(제목·블라인드) 동봉. parentId는 MVP에서 생략. */
public record MyCommentResponse(
        Long id,
        String content,
        boolean blinded,
        Instant createdAt,
        Long postId,
        String postTitle,
        boolean postBlinded
) {
}
