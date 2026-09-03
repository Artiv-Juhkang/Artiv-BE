package com.juhkang.artiv.domain.ontology;

/**
 * 열람 유입 경로. "독자가 어디서 왔는가"에 답하기 위한 계측 축.
 * 값 추가는 가산 — 프론트가 새 경로를 보내기 시작해도 기존 집계는 깨지지 않는다.
 */
public enum EntryPoint {
    DISCOVER("디스커버"),
    SEARCH("검색"),
    NOTIFICATION("알림"),
    SUBSCRIPTION("구독"),
    AUTHOR("작가 페이지"),
    DIRECT("직접 진입");

    private final String label;

    EntryPoint(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
