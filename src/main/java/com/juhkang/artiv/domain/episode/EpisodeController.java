package com.juhkang.artiv.domain.episode;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.juhkang.artiv.domain.auth.AuthSupport;
import com.juhkang.artiv.domain.episode.dto.EpisodeDetailResponse;
import com.juhkang.artiv.domain.episode.dto.EpisodeNoResponse;
import com.juhkang.artiv.domain.episode.dto.EpisodeSummaryResponse;
import com.juhkang.artiv.global.dto.SliceResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/series/{seriesId}/episodes")
@RequiredArgsConstructor
public class EpisodeController {

    private final EpisodeService episodeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CREATOR')")
    public EpisodeNoResponse upload(@AuthenticationPrincipal Long userId,
                                    @PathVariable Long seriesId,
                                    @RequestParam String title,
                                    @RequestParam(required = false) Instant publishAt,
                                    @RequestPart("images") List<MultipartFile> images) {
        return new EpisodeNoResponse(episodeService.upload(userId, seriesId, title, publishAt, images));
    }

    @GetMapping("/{episodeNo}")
    public EpisodeDetailResponse detail(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long seriesId,
                                        @PathVariable int episodeNo,
                                        Authentication authentication) {
        return episodeService.getDetail(seriesId, episodeNo, userId, AuthSupport.isAdmin(authentication));
    }

    @GetMapping
    public SliceResponse<EpisodeSummaryResponse> list(@AuthenticationPrincipal Long userId,
                                                      @PathVariable Long seriesId,
                                                      @PageableDefault(size = 20) Pageable pageable,
                                                      Authentication authentication) {
        return episodeService.getEpisodes(seriesId, userId, AuthSupport.isAdmin(authentication), pageable);
    }

    @PostMapping("/{episodeNo}/like")
    @ResponseStatus(HttpStatus.CREATED)
    public void like(@AuthenticationPrincipal Long userId,
                     @PathVariable Long seriesId,
                     @PathVariable int episodeNo) {
        episodeService.like(userId, seriesId, episodeNo);
    }

    @DeleteMapping("/{episodeNo}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(@AuthenticationPrincipal Long userId,
                       @PathVariable Long seriesId,
                       @PathVariable int episodeNo) {
        episodeService.unlike(userId, seriesId, episodeNo);
    }
}
