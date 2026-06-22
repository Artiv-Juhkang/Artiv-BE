package com.juhkang.apptoon.domain.community;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    boolean existsByUserIdAndPostId(Long userId, Long postId);
    void deleteByUserIdAndPostId(Long userId, Long postId);

    Page<PostLike> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);   // 내가 추천한 글(좋아요 시점순)
}
