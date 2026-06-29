package com.juhkang.artiv.domain.episode.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.juhkang.artiv.domain.episode.Episode;
import com.juhkang.artiv.domain.episode.EpisodeStatus;
import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.ReleasePolicy;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesStatus;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;

/** 접근 평가 순수 단위 테스트 — compute-on-read, now 주입으로 경계까지 결정적 검증. */
class EpisodeAccessEvaluatorTest {

    private final EpisodeAccessEvaluator evaluator = new EpisodeAccessEvaluator();
    private static final Instant NOW = Instant.parse("2026-06-23T00:00:00Z");

    private Series series(ReleasePolicy policy, Integer waitDays) {
        User author = User.create("a@e.com", "pw", "작가", Role.CREATOR, LocalDate.of(1990, 1, 1));
        Series s = Series.create("작품", "설명", author, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY));
        if (policy == ReleasePolicy.WAIT_FREE) {
            s.changeReleasePolicy(ReleasePolicy.WAIT_FREE, waitDays);
        }
        return s;
    }

    private Episode ep(Series s, Instant publishAt) {
        return Episode.create(s, 1, "1화", EpisodeStatus.PUBLISHED, publishAt);
    }

    @Test
    void 전체무료는_언제나_열림() {
        Series s = series(ReleasePolicy.FREE_ALL, null);
        AccessResult r = evaluator.evaluate(s, ep(s, NOW), 1L, false, NOW);
        assertThat(r.accessible()).isTrue();
        assertThat(r.lockReason()).isEqualTo(LockReason.NONE);
    }

    @Test
    void 기다리면무료_무료전환_지난_회차는_열림() {
        Series s = series(ReleasePolicy.WAIT_FREE, 7);
        Episode e = ep(s, NOW.minus(Duration.ofDays(10))); // 10일 전 발행 → freeAt=3일전
        AccessResult r = evaluator.evaluate(s, e, 1L, false, NOW);
        assertThat(r.accessible()).isTrue();
    }

    @Test
    void 기다리면무료_최신_잠긴_회차는_잠기고_freeAt를_준다() {
        Series s = series(ReleasePolicy.WAIT_FREE, 7);
        Episode e = ep(s, NOW.minus(Duration.ofDays(2))); // 2일 전 발행 → freeAt=5일 후
        AccessResult r = evaluator.evaluate(s, e, 1L, false, NOW);
        assertThat(r.accessible()).isFalse();
        assertThat(r.lockReason()).isEqualTo(LockReason.WAIT);
        assertThat(r.freeAt()).isEqualTo(NOW.plus(Duration.ofDays(5)));
    }

    @Test
    void 경계_now가_freeAt와_같으면_열림() {
        Series s = series(ReleasePolicy.WAIT_FREE, 7);
        Episode e = ep(s, NOW.minus(Duration.ofDays(7))); // freeAt == NOW
        assertThat(evaluator.evaluate(s, e, 1L, false, NOW).accessible()).isTrue();
    }

    @Test
    void 작가_admin_프리뷰는_잠긴_회차도_열림_publishAt_null도_안전() {
        Series s = series(ReleasePolicy.WAIT_FREE, 7);
        Episode draft = Episode.create(s, 2, "예약", EpisodeStatus.DRAFT, null); // publishAt null
        AccessResult r = evaluator.evaluate(s, draft, 1L, true, NOW); // isPrivileged
        assertThat(r.accessible()).isTrue();
    }
}
