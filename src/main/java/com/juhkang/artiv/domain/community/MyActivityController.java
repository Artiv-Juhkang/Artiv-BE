package com.juhkang.artiv.domain.community;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.artiv.domain.community.dto.MyCommentResponse;
import com.juhkang.artiv.domain.community.dto.MyPostResponse;
import com.juhkang.artiv.domain.community.dto.PostResponse;
import com.juhkang.artiv.global.dto.PageResponse;

import lombok.RequiredArgsConstructor;

/** 내 커뮤니티 활동내역 — 본인(@AuthenticationPrincipal)만. 내 글·내 댓글·추천한 글. */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MyActivityController {

    private final PostService postService;
    private final PostCommentService postCommentService;

    @GetMapping("/posts")
    public PageResponse<MyPostResponse> myPosts(@AuthenticationPrincipal Long userId,
                                                @PageableDefault(size = 20) Pageable pageable) {
        return postService.getMyPosts(userId, pageable);
    }

    @GetMapping("/post-comments")
    public PageResponse<MyCommentResponse> myComments(@AuthenticationPrincipal Long userId,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        return postCommentService.getMyComments(userId, pageable);
    }

    @GetMapping("/liked-posts")
    public PageResponse<PostResponse> myLikedPosts(@AuthenticationPrincipal Long userId,
                                                   @PageableDefault(size = 20) Pageable pageable) {
        return postService.getMyLikedPosts(userId, pageable);
    }

    @GetMapping("/author-news-feed")
    public PageResponse<PostResponse> authorNewsFeed(@AuthenticationPrincipal Long userId,
                                                     @PageableDefault(size = 20) Pageable pageable) {
        return postService.getAuthorNewsFeed(userId, pageable);
    }
}
