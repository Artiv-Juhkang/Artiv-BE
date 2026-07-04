package com.juhkang.artiv.domain.community;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCommentLikeRepository extends JpaRepository<PostCommentLike, Long> {
    boolean existsByUserIdAndCommentId(Long userId, Long commentId);
    void deleteByUserIdAndCommentId(Long userId, Long commentId);
    List<PostCommentLike> findByUserIdAndCommentIdIn(Long userId, Collection<Long> commentIds); // 목록 liked 배치 판정
}
