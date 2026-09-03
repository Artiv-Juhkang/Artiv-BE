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
        List<String> applicableActions) {

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
}
