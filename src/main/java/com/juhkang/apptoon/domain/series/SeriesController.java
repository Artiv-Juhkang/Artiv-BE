package com.juhkang.apptoon.domain.series;

import java.time.DayOfWeek;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.apptoon.domain.series.dto.SeriesCreateRequest;
import com.juhkang.apptoon.domain.series.dto.SeriesResponse;
import com.juhkang.apptoon.domain.series.dto.SeriesSummaryResponse;
import com.juhkang.apptoon.global.dto.IdResponse;
import com.juhkang.apptoon.global.dto.PageResponse;

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
            @PageableDefault(size = 20) Pageable pageable) {
        return seriesService.getList(day, ageRating, pageable);
    }

    @GetMapping("/{id}")
    public SeriesResponse detail(@PathVariable Long id) {
        return seriesService.getDetail(id);
    }
}
