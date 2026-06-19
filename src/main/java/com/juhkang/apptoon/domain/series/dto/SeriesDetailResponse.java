package com.juhkang.apptoon.domain.series.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Set;

import com.juhkang.apptoon.domain.series.AgeRating;
import com.juhkang.apptoon.domain.series.Series;
import com.juhkang.apptoon.domain.series.SeriesStatus;

/**
 * 작품 상세 응답 — 기본 정보에 발행회차수·최신회차번호·구독여부를 동봉해
 * 프론트가 상세 화면을 1요청으로 그리게 한다. (admin 변경 응답용 SeriesResponse와 분리)
 */
public record SeriesDetailResponse(
        Long id,
        String title,
        String description,
        String authorNickname,
        AgeRating ageRating,
        SeriesStatus status,
        Set<DayOfWeek> publishDays,
        boolean visible,
        boolean adultOnly,
        Instant createdAt,
        int episodeCount,
        int latestEpisodeNo,
        boolean isSubscribed
) {

    public static SeriesDetailResponse of(Series series, int episodeCount, int latestEpisodeNo, boolean isSubscribed) {
        return new SeriesDetailResponse(
                series.getId(),
                series.getTitle(),
                series.getDescription(),
                series.getAuthor().getNickname(),
                series.getAgeRating(),
                series.getStatus(),
                series.getPublishDays(),
                series.isVisible(),
                series.isAdultOnly(),
                series.getCreatedAt(),
                episodeCount,
                latestEpisodeNo,
                isSubscribed
        );
    }
}
