package com.juhkang.artiv.domain.community;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.artiv.domain.community.dto.PostCategoryCreateRequest;
import com.juhkang.artiv.domain.community.dto.PostCategoryResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 게시글 카테고리 등록제(C7) — 모든 인증 사용자가 조회·등록 가능, 삭제 없음. */
@RestController
@RequestMapping("/api/post-categories")
@RequiredArgsConstructor
public class PostCategoryController {

    private final PostCategoryService postCategoryService;

    @GetMapping
    public List<PostCategoryResponse> list() {
        return postCategoryService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostCategoryResponse create(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody PostCategoryCreateRequest request) {
        return postCategoryService.create(userId, request.name());
    }
}
