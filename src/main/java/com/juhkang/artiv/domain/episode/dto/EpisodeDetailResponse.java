package com.juhkang.artiv.domain.episode.dto;

import java.time.Instant;
import java.util.List;

import com.juhkang.artiv.domain.episode.Episode;
import com.juhkang.artiv.domain.episode.EpisodeStatus;
import com.juhkang.artiv.domain.episode.access.AccessResult;
import com.juhkang.artiv.domain.episode.access.LockReason;

public record EpisodeDetailResponse(
        Long id,
        int episodeNo,
        String title,
        EpisodeStatus status,
        Instant publishAt,
        List<EpisodeImageResponse> images,
        long viewCount,
        long likeCount,
        boolean liked,
        boolean locked,
        LockReason lockReason,
        Instant freeAt
) {

    public static EpisodeDetailResponse of(Episode episode, List<EpisodeImageResponse> images,
                                           long likeCount, boolean liked, AccessResult access) {
        return new EpisodeDetailResponse(
                episode.getId(),
                episode.getEpisodeNo(),
                episode.getTitle(),
                episode.getStatus(),
                episode.getPublishAt(),
                images,
                episode.getViewCount(),
                likeCount,
                liked,
                !access.accessible(),
                access.lockReason(),
                access.freeAt()
        );
    }

    /** 잠긴 회차: 메타+freeAt만, 이미지·좋아요수 노출 안 함(이미지 URL 유추 차단). */
    public static EpisodeDetailResponse locked(Episode episode, AccessResult access) {
        return new EpisodeDetailResponse(
                episode.getId(),
                episode.getEpisodeNo(),
                episode.getTitle(),
                episode.getStatus(),
                episode.getPublishAt(),
                List.of(),
                0L,
                0L,
                false,
                true,
                access.lockReason(),
                access.freeAt()
        );
    }
}
