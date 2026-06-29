package com.juhkang.artiv.domain.series;

import java.time.DayOfWeek;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.artiv.domain.series.dto.SeriesCreateRequest;
import com.juhkang.artiv.domain.series.dto.SeriesDetailResponse;
import com.juhkang.artiv.domain.auth.AuthSupport;
import com.juhkang.artiv.domain.series.dto.SeriesGenreTagsRequest;
import com.juhkang.artiv.domain.series.dto.SeriesGenreTagsResponse;
import com.juhkang.artiv.domain.series.dto.SeriesReleasePolicyRequest;
import com.juhkang.artiv.domain.series.dto.SeriesReleasePolicyResponse;
import com.juhkang.artiv.domain.series.dto.SeriesSummaryResponse;
import com.juhkang.artiv.global.dto.IdResponse;
import com.juhkang.artiv.global.dto.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/series")
@RequiredArgsConstructor
public class SeriesController {

    private final SeriesService seriesService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CREATOR')")
    public IdResponse create(@AuthenticationPrincipal Long userId,
                             @Valid @RequestBody SeriesCreateRequest request) {
        return new IdResponse(seriesService.create(userId, request));
    }

    @GetMapping
    public PageResponse<SeriesSummaryResponse> list(
            @RequestParam(required = false) DayOfWeek day,
            @RequestParam(required = false) AgeRating ageRating,
            @RequestParam(required = false) Genre genre,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean adultOnly,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "LATEST") SeriesSort sort,
            @PageableDefault(size = 20) Pageable pageable) {
        return seriesService.getList(day, ageRating, genre, keyword, adultOnly, tag, sort, pageable);
    }

    @PatchMapping("/{id}/genre-tags")
    @PreAuthorize("hasRole('CREATOR')")
    public SeriesGenreTagsResponse updateGenreTags(@AuthenticationPrincipal Long userId,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody SeriesGenreTagsRequest request) {
        return seriesService.updateGenreTags(userId, id, request.genre(), request.tags());
    }

    @PatchMapping("/{id}/release-policy")
    @PreAuthorize("hasRole('CREATOR')")
    public SeriesReleasePolicyResponse updateReleasePolicy(@AuthenticationPrincipal Long userId,
                                                           @PathVariable Long id,
                                                           @Valid @RequestBody SeriesReleasePolicyRequest request) {
        return seriesService.updateReleasePolicy(userId, id, request.mode(), request.waitFreeDays());
    }

    @GetMapping("/mine")
    public List<SeriesSummaryResponse> mySeries(@AuthenticationPrincipal Long userId) {
        return seriesService.getMySeries(userId);
    }

    @GetMapping("/{id}")
    public SeriesDetailResponse detail(@AuthenticationPrincipal Long userId,
                                       @PathVariable Long id,
                                       Authentication authentication) {
        return seriesService.getDetail(id, userId, AuthSupport.isAdmin(authentication));
    }
}
