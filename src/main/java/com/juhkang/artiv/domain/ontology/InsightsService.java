package com.juhkang.artiv.domain.ontology;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.domain.ontology.dto.WorkInsightsResponse;
import com.juhkang.artiv.domain.ontology.dto.WorkInsightsResponse.Cliff;
import com.juhkang.artiv.domain.ontology.dto.WorkInsightsResponse.EntryPointShare;
import com.juhkang.artiv.domain.ontology.dto.WorkInsightsResponse.RetentionPoint;
import com.juhkang.artiv.domain.ontology.dto.WorkInsightsResponse.SegmentSize;
import com.juhkang.artiv.domain.ontology.dto.WorkInsightsResponse.Summary;
import com.juhkang.artiv.domain.ontology.dto.WorkInsightsResponse.Window;
import com.juhkang.artiv.domain.series.Series;

import lombok.RequiredArgsConstructor;

/**
 * 작품 진단 — 잔존·절벽·유입·세그먼트를 온디맨드로 계산한다.
 *
 * 집계 테이블도 MV도 두지 않는다: 데이터 규모가 작고, 배치를 추가하면 "집계가 안 돌았다"는
 * 새 실패 모드가 생긴다. 느려지면 idx_reading_events_* 위에 MV를 올리는 것이 승격 경로다.
 */
@Service
@RequiredArgsConstructor
public class InsightsService {

    /** 분석 창(일) — 요약·유입경로·세그먼트에만 적용한다. 잔존 곡선은 생애 전체를 본다. */
    public static final int WINDOW_DAYS = 30;
    /** 저표본 기준 — 이보다 적으면 비율을 노출하지 않는다. */
    public static final int MIN_SAMPLE_FOR_RATE = 10;
    /** 절벽 판정: 단계 생존율이 중앙값의 이 비율 미만이면 절벽. */
    public static final double CLIFF_RATIO_FACTOR = 0.65;
    /** 절벽 판정: 절대 낙폭 하한(%p) — 꼬리의 잡음을 거른다. */
    public static final double CLIFF_MIN_DROP_PP = 5.0;
    /** 절벽 판정: 직전 회차 코호트가 이만큼은 돼야 비율을 신뢰한다. */
    public static final int CLIFF_MIN_COHORT = 5;

    private final ReadingEventRepository readingEventRepository;
    private final OntologyAccessChecker accessChecker;

    @Transactional(readOnly = true)
    public WorkInsightsResponse diagnose(Long seriesId, Long userId) {
        Series work = accessChecker.requireOwnedWork(seriesId, userId);

        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofDays(WINDOW_DAYS));

        List<RetentionPoint> retention = buildRetention(seriesId);
        Cliff cliff = detectCliff(retention);
        List<RetentionPoint> marked = markCliff(retention, cliff);

        return new WorkInsightsResponse(
                work.getId(),
                work.getTitle(),
                work.getContentType().name(),
                work.getContentType().getLabel(),
                new Window(WINDOW_DAYS, from, to),
                buildSummary(seriesId, from),
                marked,
                cliff,
                buildEntryPoints(seriesId, from),
                buildSegments(seriesId, to),
                Arrays.stream(ActionType.values()).map(Enum::name).toList());
    }

    // ── 잔존 ────────────────────────────────────────────────────────────

    private List<RetentionPoint> buildRetention(Long seriesId) {
        List<Object[]> rows = readingEventRepository.retentionRows(seriesId);
        if (rows.isEmpty()) {
            return List.of();
        }
        long base = ((Number) rows.get(0)[1]).longValue();   // 최초 회차 고유 독자
        List<RetentionPoint> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            int no = ((Number) r[0]).intValue();
            long readers = ((Number) r[1]).longValue();
            long completions = r[2] == null ? 0 : ((Number) r[2]).longValue();
            long sessions = ((Number) r[3]).longValue();
            double pct = base == 0 ? 0.0 : round1(100.0 * readers / base);
            Double completionRate = sessions < MIN_SAMPLE_FOR_RATE
                    ? null : round3((double) completions / sessions);
            out.add(new RetentionPoint(no, readers, pct, completionRate, false));
        }
        return out;
    }

    /**
     * 절벽 = 단계별 조건부 잔존율이 평소보다 급격히 나쁜 첫 지점.
     *
     * 절대 %p 낙폭을 쓰지 않는 이유: 잔존은 기하급수적으로 감소하므로 1→2화의 %p 낙폭이
     * 항상 가장 크고, 후반의 진짜 절벽은 코호트가 이미 작아 %p로는 묻힌다. 2026-09-03
     * seed-truth 대조에서 실제로 불일치 5건이 전부 2화를 가리켰다(정확도 47%).
     * 그래서 retention[i]/retention[i-1] = 그 회차의 생존율을 보고, 중앙 생존율 대비
     * 65% 미만으로 떨어지는 첫 지점을 절벽으로 판정한다.
     *
     * 잡음 방어 둘: 절대 낙폭 5%p 이상, 직전 코호트 5명 이상.
     */
    public Cliff detectCliff(List<RetentionPoint> retention) {
        if (retention.size() < 3) {
            return null;
        }
        List<Double> ratios = new ArrayList<>();
        for (int i = 1; i < retention.size(); i++) {
            long prev = retention.get(i - 1).uniqueReaders();
            long curr = retention.get(i).uniqueReaders();
            ratios.add(prev == 0 ? 1.0 : (double) curr / prev);
        }
        List<Double> sorted = new ArrayList<>(ratios);
        sorted.sort(Double::compareTo);
        double medianRatio = sorted.get(sorted.size() / 2);
        double threshold = medianRatio * CLIFF_RATIO_FACTOR;

        for (int i = 0; i < ratios.size(); i++) {
            RetentionPoint prev = retention.get(i);
            RetentionPoint curr = retention.get(i + 1);
            double dropPp = prev.retentionPct() - curr.retentionPct();
            if (ratios.get(i) < threshold
                    && dropPp >= CLIFF_MIN_DROP_PP
                    && prev.uniqueReaders() >= CLIFF_MIN_COHORT) {
                return new Cliff(curr.episodeNo(), round1(dropPp));
            }
        }
        return null;
    }

    private List<RetentionPoint> markCliff(List<RetentionPoint> retention, Cliff cliff) {
        if (cliff == null) {
            return retention;
        }
        return retention.stream()
                .map(p -> p.episodeNo() == cliff.episodeNo()
                        ? new RetentionPoint(p.episodeNo(), p.uniqueReaders(), p.retentionPct(),
                                p.completionRate(), true)
                        : p)
                .toList();
    }

    // ── 요약 ────────────────────────────────────────────────────────────

    private Summary buildSummary(Long seriesId, Instant from) {
        List<Object[]> rows = readingEventRepository.summaryRows(seriesId, from);
        if (rows.isEmpty()) {
            return new Summary(0, 0, null, 0);
        }
        Object[] r = rows.get(0);
        long sessions = r[0] == null ? 0 : ((Number) r[0]).longValue();
        long readers = r[1] == null ? 0 : ((Number) r[1]).longValue();
        long completions = r[2] == null ? 0 : ((Number) r[2]).longValue();
        Double rate = sessions < MIN_SAMPLE_FOR_RATE ? null : round3((double) completions / sessions);
        return new Summary(sessions, readers, rate, sessions);
    }

    // ── 유입 경로 ───────────────────────────────────────────────────────

    private List<EntryPointShare> buildEntryPoints(Long seriesId, Instant from) {
        List<Object[]> rows = readingEventRepository.entryPointRows(seriesId, from);
        long total = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        if (total == 0) {
            return List.of();
        }
        return rows.stream()
                .map(r -> {
                    EntryPoint ep = (EntryPoint) r[0];
                    long n = ((Number) r[1]).longValue();
                    return new EntryPointShare(ep.name(), ep.getLabel(), n, round3((double) n / total));
                })
                .toList();
    }

    // ── 세그먼트 ────────────────────────────────────────────────────────

    private List<SegmentSize> buildSegments(Long seriesId, Instant now) {
        Map<AudienceSegment, Long> counts = new EnumMap<>(AudienceSegment.class);
        for (AudienceSegment s : AudienceSegment.values()) {
            counts.put(s, 0L);
        }
        for (Object[] r : readingEventRepository.readerActivityRows(seriesId)) {
            Instant last = (Instant) r[0];
            long sessions = ((Number) r[1]).longValue();
            counts.merge(classify(last, sessions, now), 1L, Long::sum);
        }
        return Arrays.stream(AudienceSegment.values())
                .map(s -> {
                    long size = counts.get(s);
                    boolean disclosed = accessChecker.isDisclosable(size);
                    return new SegmentSize(s.name(), s.getLabel(), s.getRule(),
                            disclosed ? size : null, disclosed);
                })
                .toList();
    }

    private AudienceSegment classify(Instant lastRead, long sessions, Instant now) {
        long daysSince = Duration.between(lastRead, now).toDays();
        if (daysSince > AudienceSegment.ACTIVE_DAYS) {
            return AudienceSegment.LAPSED;
        }
        if (daysSince > AudienceSegment.NEW_DAYS) {
            return AudienceSegment.AT_RISK;
        }
        return sessions >= AudienceSegment.LOYAL_MIN_SESSIONS
                ? AudienceSegment.LOYAL : AudienceSegment.NEW;
    }

    // ── 유틸 ────────────────────────────────────────────────────────────

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static Double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
