package com.juhkang.apptoon.domain.series.dto;

import java.util.List;

import com.juhkang.apptoon.domain.series.Genre;

public record SeriesGenreTagsResponse(
        Genre genre,
        List<String> tags
) {
}
