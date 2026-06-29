package com.juhkang.artiv.domain.community;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.artiv.domain.community.dto.PostResponse;
import com.juhkang.artiv.global.dto.PageResponse;

import lombok.RequiredArgsConstructor;

/** 작가 공개 프로필 — 특정 작가의 공개 글 목록(블라인드 제외). 인증 사용자 누구나. */
@RestController
@RequestMapping("/api/authors")
@RequiredArgsConstructor
public class AuthorController {

    private final PostService postService;

    @GetMapping("/{authorId}/posts")
    public PageResponse<PostResponse> authorPosts(@PathVariable Long authorId,
                                                  @PageableDefault(size = 20) Pageable pageable) {
        return postService.getAuthorPosts(authorId, pageable);
    }
}
