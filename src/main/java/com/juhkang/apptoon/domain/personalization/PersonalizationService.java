package com.juhkang.apptoon.domain.personalization;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.apptoon.domain.episode.Episode;
import com.juhkang.apptoon.domain.episode.EpisodeRepository;
import com.juhkang.apptoon.domain.episode.EpisodeStatus;
import com.juhkang.apptoon.domain.personalization.dto.SubscriptionResponse;
import com.juhkang.apptoon.domain.series.Series;
import com.juhkang.apptoon.domain.series.SeriesRepository;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;
import com.juhkang.apptoon.global.dto.SeriesMaxNo;
import com.juhkang.apptoon.global.exception.BusinessException;
import com.juhkang.apptoon.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonalizationService {

    private final SubscriptionRepository subscriptionRepository;
    private final ReadLogRepository readLogRepository;
    private final SeriesRepository seriesRepository;
    private final EpisodeRepository episodeRepository;
    private final UserRepository userRepository;

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
        if (readLogRepository.existsByUserIdAndEpisodeId(userId, episode.getId())) {
            return; // 멱등: 이미 읽음
        }
        User user = userRepository.getReferenceById(userId);
        readLogRepository.save(ReadLog.create(user, episode));
    }

    public List<SubscriptionResponse> getMySubscriptions(Long userId) {
        List<Subscription> subscriptions = subscriptionRepository.findByUserIdWithSeries(userId);
        if (subscriptions.isEmpty()) {
            return List.of();
        }
        List<Long> seriesIds = subscriptions.stream().map(s -> s.getSeries().getId()).toList();

        // 작품별 (최신 발행 회차번호)·(내가 마지막으로 읽은 회차번호)을 배치 집계 → N+1 회피
        Map<Long, Integer> latestNoBySeries = episodeRepository
                .findMaxEpisodeNoBySeriesIds(seriesIds, EpisodeStatus.PUBLISHED).stream()
                .collect(Collectors.toMap(SeriesMaxNo::getSeriesId, SeriesMaxNo::getMaxNo));
        Map<Long, Integer> lastReadNoBySeries = readLogRepository
                .findMaxReadEpisodeNo(userId, seriesIds).stream()
                .collect(Collectors.toMap(SeriesMaxNo::getSeriesId, SeriesMaxNo::getMaxNo));

        return subscriptions.stream()
                .map(subscription -> {
                    Series series = subscription.getSeries();
                    int latestNo = latestNoBySeries.getOrDefault(series.getId(), 0);
                    int lastReadNo = lastReadNoBySeries.getOrDefault(series.getId(), 0);
                    boolean up = latestNo > lastReadNo; // 최신 발행 회차 > 마지막 읽은 회차 = 새 회차 있음(UP)
                    return new SubscriptionResponse(series.getId(), series.getTitle(), latestNo, lastReadNo, up);
                })
                .toList();
    }
}
