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
import com.juhkang.artiv.domain.ontology.dto.WorkInsightsResponse.LastAction;
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
 *
 * 창 적용 범위가 지표마다 다르다 — 요약·유입경로는 최근 30일, 잔존 곡선과 세그먼트는 생애 전체다.
 * 잔존은 기간 지표가 아니라 퍼널이고, 세그먼트는 '지금 어떤 상태인가'라 창을 걸면 이탈이 사라진다.
 * 화면은 이 차이를 반드시 라벨로 밝혀야 한다(같은 응답에서 요약 독자수와 세그먼트 합계가 다르다).
 */
@Service
@RequiredArgsConstructor
public class InsightsService {

    /** 분석 창(일) — 요약·유입경로에만 적용한다. 잔존 곡선과 세그먼트는 생애 전체를 본다. */
    public static final int WINDOW_DAYS = 30;
    /** 저표본 기준 — 이보다 적으면 비율을 노출하지 않는다. */
    public static final int MIN_SAMPLE_FOR_RATE = 10;
    /** 절벽 판정: 단계 생존율이 중앙값의 이 비율 미만이면 절벽. */
    public static final double CLIFF_RATIO_FACTOR = 0.65;
    /** 절벽 판정: 직전 회차 대비 최소 상대 낙폭. 중앙 생존율이 1에 가까울 때 미세 흔들림을 거른다. */
    public static final double CLIFF_MIN_RELATIVE_DROP = 0.20;
    /** 절벽 판정: 직전 회차 코호트가 이만큼은 돼야 비율을 신뢰한다. */
    public static final int CLIFF_MIN_COHORT = 5;

    private final ReadingEventRepository readingEventRepository;
    private final OntologyActionLogRepository logRepository;
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
                Arrays.stream(ActionType.values()).map(Enum::name).toList(),
                lastAction(seriesId));
    }

    /** 성공한 최신 액션 1건. 거부된 시도는 화면에 올리지 않는다(작가가 이미 응답으로 봤다). */
    private LastAction lastAction(Long seriesId) {
        return logRepository
                .findTopByObjectIdAndResultOrderByOccurredAtDesc(seriesId, ActionResult.EXECUTED)
                .map(l -> new LastAction(l.getActionType().name(), l.getActionType().getLabel(),
                        l.getOccurredAt()))
                .orElse(null);
    }

    // ── 잔존 ────────────────────────────────────────────────────────────

    private List<RetentionPoint> buildRetention(Long seriesId) {
        List<Object[]> rows = readingEventRepository.retentionRows(seriesId);
        if (rows.isEmpty()) {
            return List.of();
        }
        // 100% 기준선 = 가장 많이 도달한 회차. '첫 회차'로 잡으면 두 경우에 무너진다:
        // (1) 계측 도입 이전에 연재된 작품은 1화에 이벤트가 없어 base가 엉뚱한 회차가 된다.
        // (2) 중간 회차부터 유입된 독자가 있으면 후반 회차가 base를 넘어 잔존이 100%를 초과한다.
        // 이 곡선은 엄밀한 코호트 퍼널이 아니라 '회차별 도달 독자' 곡선이며, 판정에 쓰는 것은
        // 절대값이 아니라 이웃 회차 간 비율이라 기준선 선택이 절벽 탐지 결과를 바꾸지 않는다.
        long base = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).max().orElse(0);
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
     * 잡음 방어도 같은 단위(비율)로 건다. 초기 구현은 '절대 낙폭 5%p 이상'을 AND로 걸었는데,
     * dropPp <= prev.retentionPct()가 항상 성립하므로 이 조건은 곧 prev.retentionPct() >= 5%를
     * 요구하는 것이었다 — 잔존이 5% 아래로 내려간 뒤에는 코호트가 통째로 증발해도 절벽이
     * 보고되지 않는다. 폐기했다고 적어둔 절대 %p 기준이 잡음 방어라는 이름으로 되살아나
     * 장편(15화+) 후반 전체를 사각지대로 만들고 있었다(2026-09-03 리뷰에서 발견).
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
            double ratio = ratios.get(i);
            if (ratio < threshold
                    && (1.0 - ratio) >= CLIFF_MIN_RELATIVE_DROP
                    && prev.uniqueReaders() >= CLIFF_MIN_COHORT) {
                return new Cliff(curr.episodeNo(), round1(prev.retentionPct() - curr.retentionPct()));
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
            counts.merge(classify((Instant) r[0], (Instant) r[1], now), 1L, Long::sum);
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

    /**
     * 상호배타·전수 분류. 축은 둘 — 마지막 열람의 최근성(이탈 여부), 첫 열람의 최근성(신규 여부).
     * NEW를 마지막 열람으로 판정하면 '3개월 된 독자가 어제 다시 읽음'을 신규로 세게 된다.
     */
    private AudienceSegment classify(Instant firstRead, Instant lastRead, Instant now) {
        if (AudienceSegment.isLapsed(lastRead, now)) {
            return AudienceSegment.LAPSED;
        }
        if (Duration.between(lastRead, now).toDays() > AudienceSegment.NEW_DAYS) {
            return AudienceSegment.AT_RISK;
        }
        return Duration.between(firstRead, now).toDays() <= AudienceSegment.NEW_DAYS
                ? AudienceSegment.NEW : AudienceSegment.LOYAL;
    }

    // ── 유틸 ────────────────────────────────────────────────────────────

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static Double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
