package com.juhkang.artiv.domain.community;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCommentDislikeRepository extends JpaRepository<PostCommentDislike, Long> {
    boolean existsByUserIdAndCommentId(Long userId, Long commentId);
    void deleteByUserIdAndCommentId(Long userId, Long commentId);
    List<PostCommentDislike> findByUserIdAndCommentIdIn(Long userId, Collection<Long> commentIds); // 목록 disliked 배치 판정
}
