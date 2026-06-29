package com.juhkang.artiv.domain.personalization.dto;

import java.time.Instant;

/** 열람한 작품 항목 — 작품별 마지막 본 회차. (Series에 표지 필드 없어 썸네일 미포함) */
public record ReadHistoryResponse(
        Long seriesId,
        String seriesTitle,
        int lastReadEpisodeNo,
        Instant lastReadAt
) {
}
