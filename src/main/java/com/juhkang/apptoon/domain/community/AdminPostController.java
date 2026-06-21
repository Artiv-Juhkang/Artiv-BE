package com.juhkang.apptoon.domain.community;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.apptoon.domain.community.dto.PostAdminResponse;
import com.juhkang.apptoon.global.dto.PageResponse;

import lombok.RequiredArgsConstructor;

/** 관리자 커뮤니티 관리 — 블라인드 포함 전체 목록 + 블라인드/해제. (삭제는 /api/posts/{id} 공용.) */
@RestController
@RequestMapping("/api/admin/posts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPostController {

    private final PostService postService;

    @GetMapping
    public PageResponse<PostAdminResponse> list(@PageableDefault(size = 30) Pageable pageable) {
        return postService.getForAdmin(pageable);
    }

    @PatchMapping("/{id}/blind")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void blind(@PathVariable Long id) {
        postService.setBlinded(id, true);
    }

    @PatchMapping("/{id}/unblind")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblind(@PathVariable Long id) {
        postService.setBlinded(id, false);
    }
}
