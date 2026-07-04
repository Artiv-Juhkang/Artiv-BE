package com.juhkang.artiv.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostDislikeRepository extends JpaRepository<PostDislike, Long> {
    boolean existsByUserIdAndPostId(Long userId, Long postId);
    void deleteByUserIdAndPostId(Long userId, Long postId);
}
