package com.juhkang.artiv.domain.ontology;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.artiv.domain.episode.Episode;
import com.juhkang.artiv.domain.episode.EpisodeRepository;
import com.juhkang.artiv.domain.ontology.dto.OntologySchemaResponse;
import com.juhkang.artiv.domain.ontology.dto.ReadingEventRequest;
import com.juhkang.artiv.domain.ontology.dto.WorkInsightsResponse;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 온톨로지 — 계측 수집 + 스키마 + 진단.
 *
 * 계측은 fire-and-forget 의미론이라 202를 반환한다: 프론트는 응답을 기다리지 않고,
 * 이 엔드포인트가 실패해도 뷰어는 정상 동작해야 한다.
 */
@RestController
@RequiredArgsConstructor
public class OntologyController {

    private final EpisodeRepository episodeRepository;
    private final ReadingEventRepository readingEventRepository;
    private final InsightsService insightsService;

    @GetMapping("/api/ontology/schema")
    public OntologySchemaResponse schema() {
        return OntologySchemaResponse.of();
    }

    @GetMapping("/api/ontology/works/{seriesId}/insights")
    @PreAuthorize("hasRole('CREATOR')")
    public WorkInsightsResponse insights(@AuthenticationPrincipal Long userId,
                                         @PathVariable Long seriesId) {
        return insightsService.diagnose(seriesId, userId);
    }

    @PostMapping("/api/reading-events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void collect(@AuthenticationPrincipal Long userId,
                        @Valid @RequestBody ReadingEventRequest req) {
        Episode episode = episodeRepository
                .findBySeriesIdAndEpisodeNo(req.seriesId(), req.episodeNo())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        readingEventRepository.save(ReadingEvent.record(
                userId, req.seriesId(), episode.getId(), req.episodeNo(),
                req.entryPoint(), req.progressPct().shortValue(), req.completed(),
                req.dwellMs(), req.sessionId()));
    }
}
