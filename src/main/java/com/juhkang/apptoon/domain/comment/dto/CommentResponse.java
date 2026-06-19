package com.juhkang.apptoon.domain.comment.dto;

import java.time.Instant;

import com.juhkang.apptoon.domain.comment.Comment;

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
