package com.juhkang.artiv.domain.comment;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    boolean existsByUserIdAndCommentId(Long userId, Long commentId);

    long deleteByUserIdAndCommentId(Long userId, Long commentId);

    /** 댓글 삭제 시 그 댓글(+대댓글)의 좋아요를 FK 위반 없이 먼저 정리. */
    long deleteByCommentIdIn(Collection<Long> commentIds);

    /** 목록의 여러 댓글 좋아요를 한 번에 조회 — 카운트/내가누른여부를 메모리에서 집계(N+1 회피). */
    List<CommentLike> findByCommentIdIn(Collection<Long> commentIds);
}
