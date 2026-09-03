package com.juhkang.artiv.domain.ontology;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 열람 이벤트(append-only). 재열람·진도율·유입경로 분석 전용이며 read_logs와 공존한다.
 *
 * BaseEntity를 상속하지 않는다 — 이벤트는 생성 후 변경되지 않으므로 updated_at이 무의미하고,
 * occurred_at이 곧 생성 시각이라 created_at은 중복이다. 대량 적재 테이블에서 컬럼 2개는 비용이다.
 *
 * 연관관계 대신 비-연관 Long을 쓴다(Follow와 동일 관례) — 집계 위주라 그래프 탐색이 필요 없고,
 * 조인 없이 series_id로 바로 필터링하는 것이 인덱스 설계와 맞는다.
 */
@Entity
@Table(name = "reading_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** 비로그인 열람 대비 nullable. 탈퇴 시 NULL로 익명화된다(FK on delete set null). */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "series_id", nullable = false)
    private Long seriesId;

    @Column(name = "episode_id", nullable = false)
    private Long episodeId;

    /** 비정규화 — 잔존 곡선의 X축. 발행 후 불변이라 안전하다. */
    @Column(name = "episode_no", nullable = false)
    private int episodeNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_point", nullable = false, length = 20)
    private EntryPoint entryPoint;

    @Column(name = "progress_pct", nullable = false)
    private short progressPct;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "dwell_ms", nullable = false)
    private int dwellMs;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    private ReadingEvent(Instant occurredAt, Long userId, Long seriesId, Long episodeId, int episodeNo,
                         EntryPoint entryPoint, short progressPct, boolean completed, int dwellMs, UUID sessionId) {
        this.occurredAt = occurredAt;
        this.userId = userId;
        this.seriesId = seriesId;
        this.episodeId = episodeId;
        this.episodeNo = episodeNo;
        this.entryPoint = entryPoint;
        this.progressPct = progressPct;
        this.completed = completed;
        this.dwellMs = dwellMs;
        this.sessionId = sessionId;
    }

    public static ReadingEvent record(Long userId, Long seriesId, Long episodeId, int episodeNo,
                                      EntryPoint entryPoint, short progressPct, boolean completed,
                                      int dwellMs, UUID sessionId) {
        return recordAt(Instant.now(), userId, seriesId, episodeId, episodeNo,
                entryPoint, progressPct, completed, dwellMs, sessionId);
    }

    /**
     * 발생 시각을 지정해 생성. 세그먼트(AT_RISK·LAPSED)와 30일 창 로직은 과거 시각이 있어야
     * 검증할 수 있는데 record()는 now로 고정돼 어떤 테스트도 그 경로를 밟지 못했다. 테스트 심이다.
     */
    public static ReadingEvent recordAt(Instant occurredAt, Long userId, Long seriesId, Long episodeId,
                                        int episodeNo, EntryPoint entryPoint, short progressPct,
                                        boolean completed, int dwellMs, UUID sessionId) {
        return new ReadingEvent(occurredAt, userId, seriesId, episodeId, episodeNo,
                entryPoint, progressPct, completed, dwellMs, sessionId);
    }
}
