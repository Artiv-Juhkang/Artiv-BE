package com.juhkang.artiv.domain.episode.access;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.juhkang.artiv.domain.episode.Episode;
import com.juhkang.artiv.domain.episode.EpisodeStatus;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesAccessChecker;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 회차 단위 상호작용(좋아요·북마크·댓글)의 공통 접근 가드.
 * 회차 상세 조회와 동일한 규칙을 한 곳에 모아 각 상호작용이 제각각 검증하던 갭(F5·F13)을 없앤다:
 *   비공개(visible=false) → 작가 본인만, 그 외 404
 *   19금(AGE_19)         → 만 19세 이상만, 그 외 403(ADULT_ONLY)
 *   미발행(SCHEDULED/DRAFT) → 작가 본인만, 그 외 404(존재 숨김)
 *   잠김(기다리면무료 미전환) → 403(FORBIDDEN)
 */
@Component
@RequiredArgsConstructor
public class EpisodeAccessGuard {

    private final SeriesAccessChecker seriesAccessChecker;
    private final EpisodeAccessEvaluator evaluator;

    /** 비공개·19금·미발행 검증(잠금 정책은 호출부가 결정). 미발행은 작가 본인 아니면 404. */
    public void verifyVisibleAndPublished(Series series, Episode episode, Long viewerId) {
        seriesAccessChecker.verifyInteractable(series, viewerId); // visible(404) + adult(403)
        if (episode.getStatus() != EpisodeStatus.PUBLISHED && !series.isAuthoredBy(viewerId)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
    }

    /** 위 + 잠긴 회차 403. 좋아요·북마크·댓글 읽기/쓰기용(실제 열람 가능한 회차만 상호작용 허용). */
    public void verifyInteractable(Series series, Episode episode, Long viewerId) {
        verifyVisibleAndPublished(series, episode, viewerId);
        boolean privileged = series.isAuthoredBy(viewerId);
        if (!evaluator.evaluate(series, episode, viewerId, privileged, Instant.now()).accessible()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
