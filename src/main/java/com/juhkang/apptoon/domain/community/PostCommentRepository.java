package com.juhkang.apptoon.domain.community;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {
    List<PostComment> findByPostIdAndBlindedFalseOrderByIdAsc(Long postId);

    Page<PostComment> findByAuthorIdOrderByIdDesc(Long authorId, Pageable pageable);   // 내 댓글(블라인드 포함)
}
