package com.juhkang.artiv.domain.community;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.juhkang.artiv.domain.auth.AuthSupport;
import com.juhkang.artiv.domain.community.dto.PostDetailResponse;
import com.juhkang.artiv.domain.community.dto.PostResponse;
import com.juhkang.artiv.domain.community.dto.PostUpdateRequest;
import com.juhkang.artiv.global.dto.IdResponse;
import com.juhkang.artiv.global.dto.PageResponse;

import lombok.RequiredArgsConstructor;

/** 커뮤니티 게시글 — 작성(이미지)·목록·상세·삭제·추천. 모든 인증 사용자. */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse create(@AuthenticationPrincipal Long userId,
                             @RequestParam PostCategory category,
                             @RequestParam String title,
                             @RequestParam String content,
                             @RequestPart(name = "images", required = false) List<MultipartFile> images) {
        return new IdResponse(postService.create(userId, category, title, content, images));
    }

    @GetMapping
    public PageResponse<PostResponse> list(@RequestParam(required = false) PostCategory category,
                                           @RequestParam(defaultValue = "LATEST") PostSort sort,
                                           @PageableDefault(size = 20) Pageable pageable) {
        return postService.getList(category, sort, pageable);
    }

    @GetMapping("/{id}")
    public PostDetailResponse detail(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                     Authentication authentication) {
        return postService.getDetail(id, userId, AuthSupport.isAdmin(authentication));
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                       @RequestBody PostUpdateRequest request) {
        postService.update(userId, id, request.category(), request.title(), request.content());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id, Authentication authentication) {
        postService.delete(userId, AuthSupport.isAdmin(authentication), id);
    }

    @PostMapping("/{id}/like")
    public void like(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        postService.like(userId, id);
    }

    @DeleteMapping("/{id}/like")
    public void unlike(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        postService.unlike(userId, id);
    }
}
