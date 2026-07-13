package com.juhkang.artiv.domain.community.dto;

/** 게시글 수정 — 텍스트 필드만(이미지 교체는 확정 D-확2로 범위 밖). 검증은 서비스 레벨(작성과 동일 규칙). */
public record PostUpdateRequest(
        String category,
        String title,
        String content
) {
}
