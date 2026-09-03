package com.juhkang.artiv.domain.ontology;

/**
 * 온톨로지 액션 실행 결과. 감사 로그에 그대로 저장된다.
 *
 * 세 값뿐이다. NO_RECIPIENTS는 만들지 않는다 — 수신자 0명은 isDisclosable(0)=false로
 * BLOCKED_TOO_SMALL에 항상 흡수돼 도달 불가한 값이다(CLAUDE.md §2: 발생 불가능한 시나리오 배제).
 */
public enum ActionResult {
    /** 실행됨. 이 결과만 주 1회 스로틀의 한 주를 소모한다. */
    EXECUTED,
    /** 같은 작품에 최근 7일 내 EXECUTED가 있어 거부. */
    BLOCKED_THROTTLED,
    /** 최종 수신자가 k(MIN_SEGMENT_SIZE) 미만이라 거부. */
    BLOCKED_TOO_SMALL
}
