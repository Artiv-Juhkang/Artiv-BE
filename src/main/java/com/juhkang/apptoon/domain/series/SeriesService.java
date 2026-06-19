package com.juhkang.apptoon.domain.series;

import java.time.DayOfWeek;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.apptoon.domain.episode.EpisodeRepository;
import com.juhkang.apptoon.domain.episode.EpisodeStatus;
import com.juhkang.apptoon.domain.personalization.SubscriptionRepository;
import com.juhkang.apptoon.domain.series.dto.SeriesCreateRequest;
import com.juhkang.apptoon.domain.series.dto.SeriesDetailResponse;
import com.juhkang.apptoon.domain.series.dto.SeriesResponse;
import com.juhkang.apptoon.domain.series.dto.SeriesSummaryResponse;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;
import com.juhkang.apptoon.global.dto.PageResponse;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeriesService {

    private final SeriesRepository seriesRepository;
    private final UserRepository userRepository;
    private final EpisodeRepository episodeRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public Long create(Long authorId, SeriesCreateRequest request) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        Series series = Series.create(
                request.title(),
                request.description(),
                author,
                request.ageRating(),
                request.status(),
                request.publishDays(),
                Boolean.TRUE.equals(request.adultOnly())
        );
        return seriesRepository.save(series).getId();
    }

    public PageResponse<SeriesSummaryResponse> getList(DayOfWeek day, AgeRating ageRating, String keyword,
                                                       Boolean adultOnly, SeriesSort sort, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.toSort());
        Page<SeriesSummaryResponse> page = seriesRepository.search(day, ageRating, keyword, adultOnly, sorted)
                .map(SeriesSummaryResponse::of);
        return PageResponse.from(page);
    }

    public List<SeriesSummaryResponse> getMySeries(Long authorId) {
        return seriesRepository.findByAuthorId(authorId).stream()
                .map(SeriesSummaryResponse::of)
                .toList();
    }

    public SeriesDetailResponse getDetail(Long id, Long viewerId, boolean isAdmin) {
        Series series = seriesRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        // 비공개 작품은 작가 본인·ADMIN만 프리뷰, 그 외에는 존재를 숨겨 404
        if (!series.isVisible() && !(isAdmin || series.isAuthoredBy(viewerId))) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        int episodeCount = episodeRepository.countBySeriesIdAndStatus(id, EpisodeStatus.PUBLISHED);
        int latestEpisodeNo = episodeRepository.findMaxEpisodeNoBySeriesIdAndStatus(id, EpisodeStatus.PUBLISHED);
        boolean isSubscribed = subscriptionRepository.existsByUserIdAndSeriesId(viewerId, id);
        return SeriesDetailResponse.of(series, episodeCount, latestEpisodeNo, isSubscribed);
    }
}
