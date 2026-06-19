package com.juhkang.apptoon.domain.episode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.juhkang.apptoon.domain.episode.dto.EpisodeDetailResponse;
import com.juhkang.apptoon.domain.episode.dto.EpisodeSummaryResponse;
import com.juhkang.apptoon.domain.series.AgeRating;
import com.juhkang.apptoon.domain.series.Series;
import com.juhkang.apptoon.domain.series.SeriesRepository;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;
import com.juhkang.apptoon.global.dto.SliceResponse;
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
    private final UserRepository userRepository;
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

    public SliceResponse<EpisodeSummaryResponse> getEpisodes(Long seriesId, Long viewerId, boolean isAdmin,
                                                             Pageable pageable) {
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        verifyVisibleAccess(series, viewerId, isAdmin);
        verifyAgeAccess(series, viewerId);
        Slice<EpisodeSummaryResponse> slice = episodeRepository
                .findBySeriesIdAndStatusOrderByEpisodeNoAsc(seriesId, EpisodeStatus.PUBLISHED, pageable)
                .map(EpisodeSummaryResponse::of);
        return SliceResponse.from(slice);
    }

    public EpisodeDetailResponse getDetail(Long seriesId, int episodeNo, Long viewerId, boolean isAdmin) {
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        boolean canPreview = isAdmin || series.isAuthoredBy(viewerId);
        if (!series.isVisible() && !canPreview) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        verifyAgeAccess(series, viewerId);
        Episode episode = episodeRepository.findBySeriesIdAndEpisodeNo(seriesId, episodeNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        // 미발행(SCHEDULED/DRAFT) 회차는 작가 본인·ADMIN만 프리뷰, 그 외에는 404
        if (episode.getStatus() != EpisodeStatus.PUBLISHED && !canPreview) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        List<EpisodeImage> images = episodeImageRepository.findByEpisodeIdOrderBySortOrderAsc(episode.getId());
        return EpisodeDetailResponse.of(episode, images);
    }

    /** 비공개 작품은 작가 본인·ADMIN만 접근, 그 외에는 존재를 숨겨 404. */
    private void verifyVisibleAccess(Series series, Long viewerId, boolean isAdmin) {
        if (!series.isVisible() && !(isAdmin || series.isAuthoredBy(viewerId))) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
    }

    /** 19금(AGE_19) 작품은 만 19세 이상만 열람. 권한·소유권 검증은 Service에서(불변 규칙). */
    private void verifyAgeAccess(Series series, Long viewerId) {
        if (series.getAgeRating() != AgeRating.AGE_19) {
            return;
        }
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        if (!viewer.isAdult(LocalDate.now())) {
            throw new BusinessException(ErrorCode.ADULT_ONLY);
        }
    }
}
