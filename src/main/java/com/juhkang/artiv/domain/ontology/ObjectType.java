package com.juhkang.artiv.domain.ontology;

/**
 * 온톨로지 객체 — 작가가 쓰는 업무 언어의 명사. DB 테이블이 아니다.
 *
 * derived=true인 객체는 백킹 테이블이 없고 계산으로만 존재한다. 이 구분이 온톨로지의
 * 존재 이유다 — AudienceSegment 같은 파생 객체에 액션을 걸 수 있어야 하기 때문이다.
 *
 * 개인 독자(Reader)는 의도적으로 객체가 아니다. 작가에게 노출되는 최소 단위는 항상
 * 집합(Audience/AudienceSegment)이며, 개인 식별은 온톨로지 밖에 둔다.
 */
public enum ObjectType {
    CREATOR("작가", "users", false),
    WORK("작품", "series", false),
    RELEASE("회차", "episodes", false),
    READING_SESSION("열람", "reading_events", false),
    AUDIENCE("독자군", null, true),
    AUDIENCE_SEGMENT("독자 세그먼트", null, true),
    MEDIUM("매체", "series.content_type", false),
    TOPIC("장르·태그", "series_tags", false);

    private final String label;
    private final String backing;
    private final boolean derived;

    ObjectType(String label, String backing, boolean derived) {
        this.label = label;
        this.backing = backing;
        this.derived = derived;
    }

    public String getLabel() {
        return label;
    }

    public String getBacking() {
        return backing;
    }

    public boolean isDerived() {
        return derived;
    }
}
