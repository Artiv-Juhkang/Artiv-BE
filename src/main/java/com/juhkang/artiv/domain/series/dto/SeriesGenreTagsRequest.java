package com.juhkang.artiv.domain.series.dto;

import java.util.Set;

import com.juhkang.artiv.domain.series.Genre;

/** 작가의 장르·태그 수정 요청. genre null이면 ETC, tags는 서비스에서 정규화. */
public record SeriesGenreTagsRequest(
        Genre genre,
        Set<String> tags
) {
}
