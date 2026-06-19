package com.juhkang.apptoon.domain.comment;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import com.juhkang.apptoon.domain.comment.dto.CommentCreateRequest;
import com.juhkang.apptoon.domain.comment.dto.CommentResponse;
import com.juhkang.apptoon.global.dto.IdResponse;
import com.juhkang.apptoon.global.dto.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/series/{seriesId}/episodes/{episodeNo}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse write(@AuthenticationPrincipal Long userId,
                            @PathVariable Long seriesId,
                            @PathVariable int episodeNo,
                            @Valid @RequestBody CommentCreateRequest request) {
        return new IdResponse(commentService.write(userId, seriesId, episodeNo, request.content()));
    }

    @GetMapping
    public PageResponse<CommentResponse> list(@PathVariable Long seriesId,
                                              @PathVariable int episodeNo,
                                              @PageableDefault(size = 20) Pageable pageable) {
        return commentService.getComments(seriesId, episodeNo, pageable);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId,
                       @PathVariable Long commentId,
                       Authentication authentication) {
        commentService.delete(userId, AuthSupport.isAdmin(authentication), commentId);
    }
}
