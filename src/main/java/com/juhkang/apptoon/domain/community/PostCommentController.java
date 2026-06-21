package com.juhkang.apptoon.domain.community;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.apptoon.domain.auth.AuthSupport;
import com.juhkang.apptoon.domain.community.dto.CommentCreateRequest;
import com.juhkang.apptoon.domain.community.dto.PostCommentResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 게시글 댓글·대댓글. */
@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
public class PostCommentController {

    private final PostCommentService postCommentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostCommentResponse write(@AuthenticationPrincipal Long userId,
                                     @PathVariable Long postId,
                                     @Valid @RequestBody CommentCreateRequest request) {
        return postCommentService.write(userId, postId, request.content(), request.parentId());
    }

    @GetMapping
    public List<PostCommentResponse> list(@PathVariable Long postId) {
        return postCommentService.getComments(postId);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId,
                       @PathVariable Long postId,
                       @PathVariable Long commentId,
                       Authentication authentication) {
        postCommentService.delete(userId, AuthSupport.isAdmin(authentication), commentId);
    }
}
