package com.juhkang.artiv.domain.community;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {
    List<PostImage> findByPostIdOrderBySortOrderAsc(Long postId);
}
