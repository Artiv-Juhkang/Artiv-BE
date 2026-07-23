package com.juhkang.artiv.domain.personalization.dto;

import java.time.Instant;

/** 열람한 작품 항목 — 작품별 마지막 본 회차. coverUrl은 서재 썸네일용(없으면 null). */
public record ReadHistoryResponse(
        Long seriesId,
        String seriesTitle,
        String coverUrl,
        int lastReadEpisodeNo,
        Instant lastReadAt
) {
}
