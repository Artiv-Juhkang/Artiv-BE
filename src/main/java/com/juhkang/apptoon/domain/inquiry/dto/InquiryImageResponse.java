package com.juhkang.apptoon.domain.inquiry.dto;

/** 문의 첨부 이미지 응답 — 공개 URL과 크기. */
public record InquiryImageResponse(
        String url,
        int width,
        int height,
        int sortOrder
) {
}
