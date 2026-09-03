package com.juhkang.artiv.domain.ontology.dto;

import java.time.Instant;
import java.util.List;

/**
 * 작품 진단 응답.
 *
 * 저표본 규칙: sampleSize < 10이면 비율 필드가 null이다. 없는 정밀도를 지어내지 않는다.
 * k-익명성: 세그먼트는 disclosed=false면 size가 null이다.
 */
public record WorkInsightsResponse(
        Long workId,
        String title,
        String contentType,
        String medium,
        Window window,
        Summary summary,
        List<RetentionPoint> retention,
        Cliff cliff,
        List<EntryPointShare> entryPoints,
        List<SegmentSize> segments,
        List<String> applicableActions,
        LastAction lastAction) {

    public record Window(int days, Instant from, Instant to) {
    }

    public record Summary(long sessions, long uniqueReaders, Double completionRate, long sampleSize) {
    }

    public record RetentionPoint(int episodeNo, long uniqueReaders, double retentionPct,
                                 Double completionRate, boolean cliff) {
    }

    public record Cliff(int episodeNo, double dropPct) {
    }

    public record EntryPointShare(String entryPoint, String label, long sessions, double share) {
    }

    public record SegmentSize(String segment, String label, String rule, Long size, boolean disclosed) {
    }

    /**
     * 마지막으로 실행된 액션. 이력이 없으면 null.
     *
     * 결정→액션→측정 루프의 ④는 여기까지다. "액션 이후 신규 독자 N명" 같은 복귀율 수치는
     * 만들지 않는다 — 대조군이 없어 인과가 아니고, 시더가 모든 이벤트를 과거 시각으로 한 번에
     * 넣으므로 데모에서 그 숫자는 구조적으로 0이다. 실행 사실만 정직하게 올린다.
     */
    public record LastAction(String actionType, String label, Instant occurredAt) {
    }
}
