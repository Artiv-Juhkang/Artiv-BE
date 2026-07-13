package com.juhkang.artiv.domain.community.dto;

import com.juhkang.artiv.domain.community.PostCategory;

public record PostCategoryResponse(Long id, String name) {
    public static PostCategoryResponse of(PostCategory c) {
        return new PostCategoryResponse(c.getId(), c.getName());
    }
}
