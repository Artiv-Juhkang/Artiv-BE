package com.juhkang.apptoon.domain.episode;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.juhkang.apptoon.domain.episode.dto.EpisodeDetailResponse;
import com.juhkang.apptoon.domain.episode.dto.EpisodeSummaryResponse;
import com.juhkang.apptoon.domain.series.Series;
import com.juhkang.apptoon.domain.series.SeriesRepository;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;
import com.juhkang.apptoon.global.storage.ImageStorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EpisodeService {

    private final EpisodeRepository episodeRepository;
    private final EpisodeImageRepository episodeImageRepository;
    private final SeriesRepository seriesRepository;
    private final ImageStorageService imageStorageService;

    @Transactional
    public int upload(Long userId, Long seriesId, String title, Instant publishAt, List<MultipartFile> images) {
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        if (!series.getAuthor().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Instant now = Instant.now();
        boolean scheduled = publishAt != null && publishAt.isAfter(now);
        EpisodeStatus status = scheduled ? EpisodeStatus.SCHEDULED : EpisodeStatus.PUBLISHED;
        Instant effectivePublishAt = scheduled ? publishAt : now;

        int episodeNo = episodeRepository.findMaxEpisodeNo(seriesId) + 1;
        Episode episode = episodeRepository.save(
                Episode.create(series, episodeNo, title, status, effectivePublishAt));

        List<ImageStorageService.Stored> stored = imageStorageService.store(seriesId, episodeNo, images);
        for (int order = 0; order < stored.size(); order++) {
            ImageStorageService.Stored s = stored.get(order);
            episodeImageRepository.save(EpisodeImage.create(episode, order, s.path(), s.width(), s.height()));
        }
        return episodeNo;
    }

    @Transactional
    public void publishDueEpisodes(Instant now) {
        episodeRepository.findByStatusAndPublishAtLessThanEqual(EpisodeStatus.SCHEDULED, now)
                .forEach(Episode::markPublished);
    }

    public EpisodeDetailResponse getDetail(Long seriesId, int episodeNo) {
        Episode episode = episodeRepository.findBySeriesIdAndEpisodeNo(seriesId, episodeNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        List<EpisodeImage> images = episodeImageRepository.findByEpisodeIdOrderBySortOrderAsc(episode.getId());
        return EpisodeDetailResponse.of(episode, images);
    }

    public List<EpisodeSummaryResponse> getPublishedList(Long seriesId) {
        return episodeRepository.findBySeriesIdAndStatusOrderByEpisodeNoAsc(seriesId, EpisodeStatus.PUBLISHED)
                .stream()
                .map(EpisodeSummaryResponse::of)
                .toList();
    }
}
