package com.juhkang.artiv.domain.series;

/**
 * 작품 매체 구분 — 다매체 아트 플랫폼 전환(슬라이스 2)의 1축.
 * WEBTOON만 연재요일(publishDays)·회차번호(episodeNo) 같은 웹툰 전용 속성을 갖는다.
 * 표시 라벨·뷰어 분기는 프론트가 매핑. 새 매체는 값 추가(가산)로 확장.
 */
public enum ContentType {
    WEBTOON,
    ILLUSTRATION,
    AUDIO
}
