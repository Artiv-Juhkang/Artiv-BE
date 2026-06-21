package com.juhkang.apptoon.domain.community;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.apptoon.domain.community.dto.PostAdminDetailResponse;
import com.juhkang.apptoon.domain.community.dto.PostAdminResponse;
import com.juhkang.apptoon.global.dto.PageResponse;

import lombok.RequiredArgsConstructor;

/** 관리자 커뮤니티 관리 — 검색·필터 목록, 상세, 블라인드/해제. (삭제는 /api/posts/{id} 공용.) */
@RestController
@RequestMapping("/api/admin/posts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPostController {

    private final PostService postService;

    @GetMapping
    public PageResponse<PostAdminResponse> list(@RequestParam(required = false) PostCategory category,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Boolean blinded,
                                                @PageableDefault(size = 30) Pageable pageable) {
        return postService.getForAdmin(category, keyword, blinded, pageable);
    }

    @GetMapping("/{id}")
    public PostAdminDetailResponse detail(@PathVariable Long id) {
        return postService.getAdminDetail(id);
    }

    @PatchMapping("/{id}/blind")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void blind(@AuthenticationPrincipal Long adminId, @PathVariable Long id) {
        postService.setBlinded(adminId, id, true);
    }

    @PatchMapping("/{id}/unblind")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblind(@AuthenticationPrincipal Long adminId, @PathVariable Long id) {
        postService.setBlinded(adminId, id, false);
    }
}
