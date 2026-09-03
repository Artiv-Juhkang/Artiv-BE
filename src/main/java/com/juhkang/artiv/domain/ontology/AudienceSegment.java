package com.juhkang.artiv.domain.ontology;

/**
 * 독자 세그먼트 — 파생 객체. 백킹 테이블이 없고 열람 이력에서 계산된다.
 *
 * 임계값은 근거 없는 초기값이다. 데이터가 쌓이면 분포를 보고 재조정한다(설계문서 §13).
 */
public enum AudienceSegment {
    NEW("신규", "첫 열람이 최근 14일 이내"),
    LOYAL("충성", "최근 30일 내 3회 이상 열람"),
    AT_RISK("이탈 위험", "최근 14~30일 무열람"),
    LAPSED("이탈", "최근 30일 초과 무열람");

    public static final int NEW_DAYS = 14;
    public static final int ACTIVE_DAYS = 30;
    public static final int LOYAL_MIN_SESSIONS = 3;

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
