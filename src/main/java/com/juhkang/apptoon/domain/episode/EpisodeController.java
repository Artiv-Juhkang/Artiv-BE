package com.juhkang.apptoon.domain.episode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.juhkang.apptoon.domain.episode.dto.EpisodeDetailResponse;
import com.juhkang.apptoon.domain.episode.dto.EpisodeSummaryResponse;
import com.juhkang.apptoon.global.dto.SliceResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/series/{seriesId}/episodes")
@RequiredArgsConstructor
public class EpisodeController {

    private final EpisodeService episodeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CREATOR')")
    public Map<String, Integer> upload(@AuthenticationPrincipal Long userId,
                                       @PathVariable Long seriesId,
                                       @RequestParam String title,
                                       @RequestParam(required = false) Instant publishAt,
                                       @RequestPart("images") List<MultipartFile> images) {
        return Map.of("episodeNo", episodeService.upload(userId, seriesId, title, publishAt, images));
    }

    @GetMapping("/{episodeNo}")
    public EpisodeDetailResponse detail(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long seriesId,
                                        @PathVariable int episodeNo) {
        return episodeService.getDetail(seriesId, episodeNo, userId);
    }

    @GetMapping
    public SliceResponse<EpisodeSummaryResponse> list(@AuthenticationPrincipal Long userId,
                                                      @PathVariable Long seriesId,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        return episodeService.getEpisodes(seriesId, userId, pageable);
    }
}
