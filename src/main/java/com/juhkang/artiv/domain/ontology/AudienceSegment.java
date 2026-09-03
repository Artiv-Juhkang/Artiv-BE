package com.juhkang.artiv.domain.ontology;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 독자 세그먼트 — 파생 객체. 백킹 테이블이 없고 열람 이력에서 계산된다.
 *
 * 네 값은 상호배타적이고 전수를 덮는다. 분류 축은 둘뿐이다 — 마지막 열람이 얼마나 최근인가(이탈 여부),
 * 그리고 첫 열람이 최근인가(신규 여부).
 *
 * **세그먼트는 30일 분석 창을 쓰지 않는다.** 요약·유입경로는 "최근 30일에 무슨 일이 있었나"를 묻지만
 * 세그먼트는 "이 독자가 지금 어떤 상태인가"를 묻는 생애 기준 분류다. 창을 걸면 LAPSED가 구조적으로
 * 0이 된다(창 밖 독자는 조회 자체가 안 되므로). 화면·문서 문구도 이에 맞춘다.
 *
 * 임계값(14일·30일)은 근거 없는 초기값이다. 데이터가 쌓이면 분포를 보고 재조정한다(설계문서 §13).
 */
public enum AudienceSegment {
    NEW("신규", "최근 14일 내 활동 + 첫 열람도 최근 14일 내"),
    LOYAL("지속", "최근 14일 내 활동 + 첫 열람은 그 이전"),
    AT_RISK("이탈 위험", "마지막 열람이 14~30일 전"),
    LAPSED("이탈", "마지막 열람이 30일 초과");

    /** 신규/활동 경계(일). */
    public static final int NEW_DAYS = 14;
    /** 활동으로 인정하는 최대 경과일. 이보다 오래되면 이탈. */
    public static final int ACTIVE_DAYS = 30;

    /**
     * 이탈 판정 — 화면(classify)과 발송 대상 쿼리가 **반드시 이 술어 하나를 공유해야 한다**.
     *
     * 정의가 두 벌이면 조용히 갈라진다: classify는 toDays() > 30(경과 31일 이상)인데
     * 순진한 쿼리 max(occurredAt) < now-30d 는 경과 30일 초과라, 30.0~31.0일 구간 독자가
     * 화면에서는 AT_RISK인데 발송 대상에는 들어간다. k 게이트도 한쪽 기준으로만 통과하고
     * 감사 로그의 recipient_count가 화면 숫자와 어긋난다.
     */
    public static boolean isLapsed(Instant lastRead, Instant now) {
        return Duration.between(lastRead, now).toDays() > ACTIVE_DAYS;
    }

    /**
     * 위 술어와 등가인 SQL 경계. `max(occurredAt) <= cutoff` 로 쓴다.
     * isLapsed와 같은 집합을 고르는지는 NudgeAudienceTest의 경계 케이스(30.5일/31.5일)가 지킨다.
     */
    public static Instant lapsedCutoff(Instant now) {
        return now.minus(ACTIVE_DAYS + 1L, ChronoUnit.DAYS);
    }

    private final String label;
    private final String rule;

    AudienceSegment(String label, String rule) {
        this.label = label;
        this.rule = rule;
    }

    public String getLabel() {
        return label;
    }

    public String getRule() {
        return rule;
    }
}
