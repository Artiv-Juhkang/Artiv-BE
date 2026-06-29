package com.juhkang.artiv.domain.comment.dto;

import java.time.Instant;

import com.juhkang.artiv.domain.comment.Comment;

public record CommentResponse(
        Long id,
        String content,
        String authorNickname,
        Instant createdAt
) {

    public static CommentResponse of(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getUser().getNickname(),
                comment.getCreatedAt()
        );
    }
}
