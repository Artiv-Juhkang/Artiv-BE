package com.juhkang.artiv.domain.series.dto;

import java.util.List;

import com.juhkang.artiv.domain.episode.MediaKind;
import com.juhkang.artiv.domain.series.ContentType;

/** 창작물 타입 레지스트리 항목 — 프론트가 탭·뷰어를 동적 렌더(타입=데이터). */
public record ContentTypeResponse(
        String key,
        String label,
        boolean serialized,
        List<MediaKind> assetKinds
) {

    public static ContentTypeResponse of(ContentType type) {
        return new ContentTypeResponse(type.name(), type.getLabel(), type.isSerialized(), type.getAssetKinds());
    }
}
