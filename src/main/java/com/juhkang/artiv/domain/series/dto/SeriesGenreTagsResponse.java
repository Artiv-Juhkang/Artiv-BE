package com.juhkang.artiv.domain.series.dto;

import java.util.List;

import com.juhkang.artiv.domain.series.Genre;

public record SeriesGenreTagsResponse(
        Genre genre,
        List<String> tags
) {
}
