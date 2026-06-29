package com.juhkang.artiv.domain.episode.access;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.juhkang.artiv.domain.episode.Episode;
import com.juhkang.artiv.domain.series.ReleasePolicy;
import com.juhkang.artiv.domain.series.Series;

/**
 * 회차 열람 접근권 평가(compute-on-read). 0단계는 시간계산만.
 * 미래의 코인 엔타이틀먼트·멤버십·정산은 아래 "미래 주입점 ①"에 1~2줄로 끼운다(호출부 시그니처 불변).
 */
@Component
public class EpisodeAccessEvaluator {

    /**
     * @param isPrivileged 작가 본인·관리자(호출부 canPreview 재사용). true면 항상 열림(+publishAt-null NPE 방어).
     * @param now          현재 시각(테스트 위해 주입).
     */
    public AccessResult evaluate(Series series, Episode episode, Long viewerId, boolean isPrivileged, Instant now) {
        if (isPrivileged) {
            return AccessResult.open();
        }
        if (series.getReleasePolicy() == ReleasePolicy.FREE_ALL) {
            return AccessResult.open();
        }
        // WAIT_FREE: 여기 도달 시 호출부 status 가드로 PUBLISHED 보장 → publishAt non-null.
        Instant freeAt = episode.getPublishAt().plus(Duration.ofDays(series.getWaitFreeDays()));
        if (!now.isBefore(freeAt)) {
            return AccessResult.open();   // 무료전환 시점 도달(경계 포함)
        }
        // ── 미래 주입점 ①: 잠금 확정 직전 "이미 접근권 있나" 확인 ──
        //   1단계(유료미리보기): if (entitlementRepository.has(viewerId, episode.getId())) return AccessResult.open();
        //   2단계(멤버십):       if (membershipService.coversFreely(viewerId, series)) return AccessResult.open();
        return AccessResult.waitLocked(freeAt);
    }
}
