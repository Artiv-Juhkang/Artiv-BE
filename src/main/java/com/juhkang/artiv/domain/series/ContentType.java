package com.juhkang.artiv.domain.series;

import java.util.List;

import com.juhkang.artiv.domain.episode.MediaKind;

/**
 * 작품 매체 구분 — 다매체 아트 플랫폼. 새 매체는 값 추가(가산)로 확장한다.
 * label·serialized·assetKinds 메타를 enum 자신이 들고, GET /api/creativity/types가 그대로
 * 내려줘 프론트가 탭·뷰어를 동적 렌더한다(타입=데이터: 백엔드에 값 추가 → 프론트 코드 변경 0).
 * serialized=true(WEBTOON·NOVEL·AUDIO)만 연재요일·회차번호 같은 연재 속성을 쓴다.
 */
public enum ContentType {
    WEBTOON("웹툰", true, List.of(MediaKind.IMAGE)),
    ILLUSTRATION("일러스트", false, List.of(MediaKind.IMAGE)),
    DESIGN("디자인", false, List.of(MediaKind.IMAGE)),
    PHOTO("사진", false, List.of(MediaKind.IMAGE)),
    DRAWING("손그림", false, List.of(MediaKind.IMAGE)),
    NOVEL("소설", true, List.of(MediaKind.TEXT)),
    AUDIO("음악", true, List.of(MediaKind.AUDIO));

    private final String label;
    private final boolean serialized;
    private final List<MediaKind> assetKinds;

    ContentType(String label, boolean serialized, List<MediaKind> assetKinds) {
        this.label = label;
        this.serialized = serialized;
        this.assetKinds = assetKinds;
    }

    public String getLabel() {
        return label;
    }

    public boolean isSerialized() {
        return serialized;
    }

    public List<MediaKind> getAssetKinds() {
        return assetKinds;
    }
}
