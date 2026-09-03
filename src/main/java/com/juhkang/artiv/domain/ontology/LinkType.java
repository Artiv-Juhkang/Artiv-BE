package com.juhkang.artiv.domain.ontology;

/**
 * 온톨로지 링크 — 객체 사이의 관계.
 *
 * DIRECT    = FK/컬럼으로 이미 존재
 * AGGREGATE = 이벤트 집계로 계산
 * DERIVED   = 2-hop 등 파생 계산. 온톨로지 없이는 만들 수 없는 것들이다.
 */
public enum LinkType {
    AUTHORS("작성", ObjectType.CREATOR, ObjectType.WORK, Kind.DIRECT),
    CONTAINS("포함", ObjectType.WORK, ObjectType.RELEASE, Kind.DIRECT),
    PRECEDES("선행", ObjectType.RELEASE, ObjectType.RELEASE, Kind.DIRECT),
    READS("열람", ObjectType.AUDIENCE, ObjectType.RELEASE, Kind.AGGREGATE),
    SUBSCRIBES("구독", ObjectType.AUDIENCE, ObjectType.WORK, Kind.AGGREGATE),
    SHARES_AUDIENCE_WITH("독자 공유", ObjectType.WORK, ObjectType.WORK, Kind.DERIVED),
    IS_ABOUT("주제", ObjectType.WORK, ObjectType.TOPIC, Kind.DIRECT),
    CO_OCCURS_WITH("동시출현", ObjectType.TOPIC, ObjectType.TOPIC, Kind.DERIVED);

    public enum Kind { DIRECT, AGGREGATE, DERIVED }

    private final String label;
    private final ObjectType from;
    private final ObjectType to;
    private final Kind kind;

    LinkType(String label, ObjectType from, ObjectType to, Kind kind) {
        this.label = label;
        this.from = from;
        this.to = to;
        this.kind = kind;
    }

    public String getLabel() {
        return label;
    }

    public ObjectType getFrom() {
        return from;
    }

    public ObjectType getTo() {
        return to;
    }

    public Kind getKind() {
        return kind;
    }
}
