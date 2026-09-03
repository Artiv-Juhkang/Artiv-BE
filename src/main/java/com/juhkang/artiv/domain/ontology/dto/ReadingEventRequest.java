package com.juhkang.artiv.domain.ontology.dto;

import java.util.UUID;

import com.juhkang.artiv.domain.ontology.EntryPoint;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReadingEventRequest(
        @NotNull Long seriesId,
        @NotNull Integer episodeNo,
        @NotNull EntryPoint entryPoint,
        @NotNull @Min(0) @Max(100) Integer progressPct,
        @NotNull Boolean completed,
        @NotNull @Min(0) Integer dwellMs,
        @NotNull UUID sessionId) {
}
