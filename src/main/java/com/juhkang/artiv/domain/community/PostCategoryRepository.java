package com.juhkang.artiv.domain.community;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCategoryRepository extends JpaRepository<PostCategory, Long> {
    boolean existsByName(String name);
    List<PostCategory> findAllByOrderByIdAsc();
}
