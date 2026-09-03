package com.juhkang.artiv.domain.ontology;

/**
 * 온톨로지 액션 — 온톨로지 객체를 대상으로 실제 상태를 바꾸는 것. 조회는 액션이 아니다.
 *
 * 대부분 기존 엔드포인트를 가리킨다(신규 엔드포인트 0). 프론트는 이 메타를 읽어
 * 버튼을 렌더하므로, 액션을 추가해도 프론트 코드는 변하지 않는다.
 *
 * 발행 시각 조정(SCHEDULE_RELEASE)은 백킹 엔드포인트가 없어 제외했다 —
 * EpisodeController에 회차 수정 경로가 없고 publishAt은 업로드 시점에만 설정된다.
 * 설계문서 §3-3-1 참조.
 */
public enum ActionType {
    RETAG_WORK("태그 수정", ObjectType.WORK, "PATCH", "/api/series/{id}/genre-tags"),
    CHANGE_RELEASE_POLICY("공개정책 변경", ObjectType.WORK, "PATCH", "/api/series/{id}/release-policy"),
    NUDGE_LAPSED_AUDIENCE("이탈 독자 알림", ObjectType.AUDIENCE_SEGMENT, "POST",
            "/api/ontology/actions/nudge-lapsed-audience");

    private final String label;
    private final ObjectType target;
    private final String method;
    private final String endpoint;

    ActionType(String label, ObjectType target, String method, String endpoint) {
        this.label = label;
        this.target = target;
        this.method = method;
        this.endpoint = endpoint;
    }

    public String getLabel() {
        return label;
    }

    public ObjectType getTarget() {
        return target;
    }

    public String getMethod() {
        return method;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
