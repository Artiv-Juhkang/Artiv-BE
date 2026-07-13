package com.juhkang.artiv.domain.community;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.domain.community.dto.PostCategoryResponse;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 게시글 카테고리 등록제(C7, 확정 D1=B) — 삭제 없음(가산 전용, 글이 매달려 있을 수 있음). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCategoryService {

    private final PostCategoryRepository postCategoryRepository;

    public List<PostCategoryResponse> list() {
        return postCategoryRepository.findAllByOrderByIdAsc().stream().map(PostCategoryResponse::of).toList();
    }

    @Transactional
    public PostCategoryResponse create(Long userId, String name) {
        String trimmed = name.strip();
        if (postCategoryRepository.existsByName(trimmed)) {
            throw new BusinessException(ErrorCode.DUPLICATE_POST_CATEGORY);
        }
        return PostCategoryResponse.of(postCategoryRepository.save(PostCategory.create(trimmed, userId)));
    }
}
