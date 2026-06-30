package com.juhkang.artiv.domain.series;

import java.time.DayOfWeek;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.domain.episode.EpisodeRepository;
import com.juhkang.artiv.domain.episode.EpisodeStatus;
import com.juhkang.artiv.domain.personalization.SubscriptionRepository;
import com.juhkang.artiv.domain.series.dto.SeriesCreateRequest;
import com.juhkang.artiv.domain.series.dto.SeriesDetailResponse;
import com.juhkang.artiv.domain.series.dto.SeriesGenreTagsResponse;
import com.juhkang.artiv.domain.series.dto.SeriesReleasePolicyResponse;
import com.juhkang.artiv.domain.series.dto.SeriesResponse;
import com.juhkang.artiv.domain.series.dto.SeriesSummaryResponse;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;
import com.juhkang.artiv.global.dto.PageResponse;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

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
                request.publishDays() != null ? request.publishDays() : Set.of(),
                Boolean.TRUE.equals(request.adultOnly()),
                request.contentType() != null ? request.contentType() : ContentType.WEBTOON
        );
        series.changeGenre(request.genre() != null ? request.genre() : Genre.ETC);
        series.replaceTags(normalizeTags(request.tags()));
        return seriesRepository.save(series).getId();
    }

    @Transactional
    public SeriesGenreTagsResponse updateGenreTags(Long authorId, Long seriesId, Genre genre, Set<String> tags) {
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        if (!series.isAuthoredBy(authorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        series.changeGenre(genre != null ? genre : Genre.ETC);
        series.replaceTags(normalizeTags(tags));
        return new SeriesGenreTagsResponse(series.getGenre(), List.copyOf(series.getTags()));
    }

    /** 작가 공개정책(수익화 0단계) 변경 — 작가 본인만. WAIT_FREE면 waitFreeDays>0(엔티티 불변식). */
    @Transactional
    public SeriesReleasePolicyResponse updateReleasePolicy(Long authorId, Long seriesId,
                                                           ReleasePolicy mode, Integer waitFreeDays) {
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        if (!series.isAuthoredBy(authorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        series.changeReleasePolicy(mode, waitFreeDays);
        return new SeriesReleasePolicyResponse(series.getReleasePolicy(), series.getWaitFreeDays());
    }

    public PageResponse<SeriesSummaryResponse> getList(DayOfWeek day, AgeRating ageRating, Genre genre,
                                                       ContentType contentType, String keyword,
                                                       Boolean adultOnly, String tag, SeriesSort sort, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.toSort());
        String normTag = (tag != null && !tag.isBlank()) ? tag.strip() : null;
        Page<SeriesSummaryResponse> page = seriesRepository.search(day, ageRating, genre, contentType, keyword, adultOnly, normTag, sorted)
                .map(SeriesSummaryResponse::of);
        return PageResponse.from(page);
    }

    /** 태그 정규화: 공백 제거·빈값/초과길이 제외·중복 제거·최대 10개. */
    private Set<String> normalizeTags(Set<String> raw) {
        if (raw == null) {
            return Set.of();
        }
        return raw.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(t -> !t.isBlank() && t.length() <= 30)
                .limit(10)
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
