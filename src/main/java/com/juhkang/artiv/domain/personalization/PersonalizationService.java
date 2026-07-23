package com.juhkang.artiv.domain.personalization;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.domain.episode.Episode;
import com.juhkang.artiv.domain.episode.EpisodeRepository;
import com.juhkang.artiv.domain.episode.EpisodeStatus;
import com.juhkang.artiv.domain.episode.access.EpisodeAccessEvaluator;
import com.juhkang.artiv.domain.episode.access.EpisodeAccessGuard;
import com.juhkang.artiv.domain.personalization.dto.BookmarkResponse;
import com.juhkang.artiv.domain.personalization.dto.ReadHistoryResponse;
import com.juhkang.artiv.domain.personalization.dto.SubscriptionResponse;
import com.juhkang.artiv.global.dto.PageResponse;
import com.juhkang.artiv.global.dto.Pageables;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesRepository;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;
import com.juhkang.artiv.global.dto.SeriesMaxNo;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonalizationService {

    private final SubscriptionRepository subscriptionRepository;
    private final ReadLogRepository readLogRepository;
    private final BookmarkRepository bookmarkRepository;
    private final SeriesRepository seriesRepository;
    private final EpisodeRepository episodeRepository;
    private final UserRepository userRepository;
    private final EpisodeAccessEvaluator episodeAccessEvaluator;
    private final EpisodeAccessGuard episodeAccessGuard;

    @Transactional
    public void subscribe(Long userId, Long seriesId) {
        if (!seriesRepository.existsById(seriesId)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        if (subscriptionRepository.existsByUserIdAndSeriesId(userId, seriesId)) {
            return; // 멱등: 이미 구독 중이면 그대로
        }
        User user = userRepository.getReferenceById(userId);
        Series series = seriesRepository.getReferenceById(seriesId);
        subscriptionRepository.save(Subscription.create(user, series));
    }

    @Transactional
    public void unsubscribe(Long userId, Long seriesId) {
        subscriptionRepository.deleteByUserIdAndSeriesId(userId, seriesId);
    }

    @Transactional
    public void markRead(Long userId, Long seriesId, int episodeNo) {
        Episode episode = episodeRepository.findBySeriesIdAndEpisodeNo(seriesId, episodeNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        Series series = episode.getSeries();
        // 비공개·19금·미발행 가드(미발행 회차는 존재를 숨겨 404 — F13).
        episodeAccessGuard.verifyVisibleAndPublished(series, episode, userId);
        // 잠긴(기다리면무료 미전환) 회차는 실제 열람 불가 → 읽음 기록만 조용히 스킵(뷰어는 애초에 잠긴 회차를
        // 열지 않으므로 예외 대신 무해한 no-op. UP·이어보기·서재 신호 정확성 + 미래 PAID seam).
        boolean isPrivileged = series.isAuthoredBy(userId);
        if (!episodeAccessEvaluator.evaluate(series, episode, userId, isPrivileged, Instant.now()).accessible()) {
            return;
        }
        if (readLogRepository.existsByUserIdAndEpisodeId(userId, episode.getId())) {
            return; // 멱등: 이미 읽음
        }
        User user = userRepository.getReferenceById(userId);
        readLogRepository.save(ReadLog.create(user, episode));
    }

    @Transactional
    public void bookmark(Long userId, Long seriesId, int episodeNo) {
        Episode episode = episodeRepository.findBySeriesIdAndEpisodeNo(seriesId, episodeNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        episodeAccessGuard.verifyInteractable(episode.getSeries(), episode, userId); // 미발행·잠긴 회차 북마크 차단(F13)
        if (bookmarkRepository.existsByUserIdAndEpisodeId(userId, episode.getId())) {
            return; // 멱등: 이미 북마크면 그대로
        }
        User user = userRepository.getReferenceById(userId);
        bookmarkRepository.save(Bookmark.create(user, episode));
    }

    @Transactional
    public void unbookmark(Long userId, Long seriesId, int episodeNo) {
        Episode episode = episodeRepository.findBySeriesIdAndEpisodeNo(seriesId, episodeNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        bookmarkRepository.deleteByUserIdAndEpisodeId(userId, episode.getId());
    }

    /** 내 북마크(최신순 Page). 정렬 고정(클라 sort 무시) — read-history와 동일 계약. */
    public PageResponse<BookmarkResponse> getMyBookmarks(Long userId, Pageable pageable) {
        return PageResponse.from(
                bookmarkRepository.findByUserIdWithEpisode(userId, Pageables.pageOnly(pageable))
                        .map(BookmarkResponse::of));
    }

    /** 내 구독(최신순 Page). 정렬 고정(클라 sort 무시) — read-history와 동일 계약. */
    public PageResponse<SubscriptionResponse> getMySubscriptions(Long userId, Pageable pageable) {
        Page<Subscription> page = subscriptionRepository
                .findByUserIdWithSeries(userId, Pageables.pageOnly(pageable));
        List<Long> seriesIds = page.getContent().stream().map(s -> s.getSeries().getId()).toList();

        // 작품별 (최신 발행 회차번호)·(내가 마지막으로 읽은 회차번호)을 배치 집계 → N+1 회피
        Map<Long, Integer> latestNoBySeries = seriesIds.isEmpty() ? Map.of()
                : episodeRepository.findMaxEpisodeNoBySeriesIds(seriesIds, EpisodeStatus.PUBLISHED).stream()
                        .collect(Collectors.toMap(SeriesMaxNo::getSeriesId, SeriesMaxNo::getMaxNo));
        Map<Long, Integer> lastReadNoBySeries = seriesIds.isEmpty() ? Map.of()
                : readLogRepository.findMaxReadEpisodeNo(userId, seriesIds).stream()
                        .collect(Collectors.toMap(SeriesMaxNo::getSeriesId, SeriesMaxNo::getMaxNo));

        return PageResponse.from(page.map(subscription -> {
            Series series = subscription.getSeries();
            int latestNo = latestNoBySeries.getOrDefault(series.getId(), 0);
            int lastReadNo = lastReadNoBySeries.getOrDefault(series.getId(), 0);
            boolean up = latestNo > lastReadNo; // 최신 발행 회차 > 마지막 읽은 회차 = 새 회차 있음(UP)
            return new SubscriptionResponse(series.getId(), series.getTitle(), series.getCoverUrl(), latestNo, lastReadNo, up);
        }));
    }

    /** 열람한 작품(최근 열람순). 작품 제목은 배치 1쿼리로 결합 → 쿼리 2개 고정(N+1 없음). 정렬 고정(클라 sort 무시). */
    public PageResponse<ReadHistoryResponse> getReadHistory(Long userId, Pageable pageable) {
        Page<ReadSeriesRow> page = readLogRepository.findReadSeries(userId, Pageables.pageOnly(pageable));
        List<Long> seriesIds = page.getContent().stream().map(ReadSeriesRow::getSeriesId).toList();
        Map<Long, Series> seriesById = seriesRepository.findAllById(seriesIds).stream()
                .collect(Collectors.toMap(Series::getId, s -> s));
        return PageResponse.from(page.map(row -> {
            Series series = seriesById.get(row.getSeriesId());
            return new ReadHistoryResponse(
                    row.getSeriesId(),
                    series != null ? series.getTitle() : null,
                    series != null ? series.getCoverUrl() : null,
                    row.getMaxNo(),
                    row.getLastReadAt());
        }));
    }
}
