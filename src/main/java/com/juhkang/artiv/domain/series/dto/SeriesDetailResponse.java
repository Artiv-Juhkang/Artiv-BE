package com.juhkang.artiv.domain.series.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Set;

import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.Genre;
import com.juhkang.artiv.domain.series.ReleasePolicy;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesStatus;

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
        Genre genre,
        Set<DayOfWeek> publishDays,
        Set<String> tags,
        boolean visible,
        boolean adultOnly,
        ReleasePolicy releasePolicy,
        Integer waitFreeDays,
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
                series.getGenre(),
                Set.copyOf(series.getPublishDays()), // LAZY 컬렉션을 트랜잭션 안에서 즉시 복사(세션밖 직렬화 방지)
                Set.copyOf(series.getTags()),        // 태그도 동일하게 트랜잭션 안에서 복사
                series.isVisible(),
                series.isAdultOnly(),
                series.getReleasePolicy(),
                series.getWaitFreeDays(),
                series.getCreatedAt(),
                episodeCount,
                latestEpisodeNo,
                isSubscribed
        );
    }
}
