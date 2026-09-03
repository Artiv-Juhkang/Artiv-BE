package com.juhkang.artiv.domain.ontology;

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
