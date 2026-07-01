package com.juhkang.artiv.domain.comment.dto;

import java.time.Instant;
import java.util.List;

import com.juhkang.artiv.domain.comment.Comment;

/**
 * 회차 댓글 응답. 최상위 댓글은 replies에 대댓글을 동봉하고, 대댓글은 replies가 빈 리스트다.
 * likeCount/liked는 서비스가 배치로 집계해 넣는다(엔티티만으론 알 수 없음).
 */
public record CommentResponse(
        Long id,
        Long parentId,
        String content,
        String authorNickname,
        Instant createdAt,
        long likeCount,
        boolean liked,
        List<CommentResponse> replies
) {

    public static CommentResponse of(Comment comment, long likeCount, boolean liked, List<CommentResponse> replies) {
        return new CommentResponse(
                comment.getId(),
                comment.getParentId(),
                comment.getContent(),
                comment.getUser().getNickname(),
                comment.getCreatedAt(),
                likeCount,
                liked,
                replies
        );
    }
}
