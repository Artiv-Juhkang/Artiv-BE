package com.juhkang.apptoon.domain.community;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {
    List<PostComment> findByPostIdAndBlindedFalseOrderByIdAsc(Long postId);
}
