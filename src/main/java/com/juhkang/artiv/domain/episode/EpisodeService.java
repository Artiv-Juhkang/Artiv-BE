package com.juhkang.artiv.domain.episode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.juhkang.artiv.domain.episode.dto.EpisodeDetailResponse;
import com.juhkang.artiv.domain.episode.dto.EpisodeImageResponse;
import com.juhkang.artiv.domain.episode.access.AccessResult;
import com.juhkang.artiv.domain.episode.access.EpisodeAccessEvaluator;
import com.juhkang.artiv.domain.episode.dto.EpisodeSummaryResponse;
import com.juhkang.artiv.domain.notification.NotificationService;
import com.juhkang.artiv.domain.comment.CommentRepository;
import com.juhkang.artiv.domain.notification.NotificationTargetType;
import com.juhkang.artiv.domain.notification.NotificationType;
import com.juhkang.artiv.domain.personalization.SubscriptionRepository;
import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesAccessChecker;
import com.juhkang.artiv.domain.series.SeriesRepository;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;
import com.juhkang.artiv.global.dto.SliceResponse;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;
import com.juhkang.artiv.global.storage.ImageStorageService;
import com.juhkang.artiv.global.storage.MediaStorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EpisodeService {

    private final EpisodeRepository episodeRepository;
    private final EpisodeImageRepository episodeImageRepository;
    private final EpisodeLikeRepository episodeLikeRepository;
    private final SeriesRepository seriesRepository;
    private final SeriesAccessChecker seriesAccessChecker;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;
    private final MediaStorageService mediaStorageService;
    private final SubscriptionRepository subscriptionRepository;
    private final NotificationService notificationService;
    private final EpisodeAccessEvaluator episodeAccessEvaluator;
    private final CommentRepository commentRepository;

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

        // 매체 종류는 작품 타입이 결정한다(WEBTOON/일러스트…=IMAGE, 소설=TEXT, 음악=AUDIO).
        // 이미지는 검증·리사이즈가 필요해 ImageStorageService, 텍스트/오디오는 원본 저장.
        MediaKind assetKind = series.getContentType().getAssetKinds().get(0);
        if (assetKind == MediaKind.IMAGE) {
            List<ImageStorageService.Stored> stored = imageStorageService.store(seriesId, episodeNo, images);
            for (int order = 0; order < stored.size(); order++) {
                ImageStorageService.Stored s = stored.get(order);
                episodeImageRepository.save(EpisodeImage.create(episode, order, s.path(), s.width(), s.height()));
            }
        } else {
            List<MediaStorageService.Stored> stored = assetKind == MediaKind.TEXT
                    ? mediaStorageService.storeText(seriesId, episodeNo, images)
                    : mediaStorageService.storeAudio(seriesId, episodeNo, images);
            for (int order = 0; order < stored.size(); order++) {
                MediaStorageService.Stored s = stored.get(order);
                episodeImageRepository.save(
                        EpisodeImage.createMedia(episode, order, s.path(), assetKind, s.mimeType(), null));
            }
        }
        if (!scheduled) {                       // 즉시 발행 → 최근업데이트 갱신 + 구독자 알림(예약은 발행 시점에)
            series.markEpisodePublished(effectivePublishAt);
            notifySubscribers(series, episode);
        }
        return episodeNo;
    }

    @Transactional
    public void publishDueEpisodes(Instant now) {
        episodeRepository.findByStatusAndPublishAtLessThanEqual(EpisodeStatus.SCHEDULED, now)
                .forEach(ep -> {
                    ep.markPublished();
                    ep.getSeries().markEpisodePublished(ep.getPublishAt());  // 최근업데이트 전진
                    notifySubscribers(ep.getSeries(), ep);   // SCHEDULED→PUBLISHED 전환 시 알림
                });
    }

    /**
     * 시리즈 구독자에게 새 회차 알림(작가 본인 제외). dedup_key=회차 PK라 발행 경로 중복돼도 멱등.
     * 목적지는 작품 상세(targetType=SERIES, targetId=시리즈 id) — 프론트가 상세로 딥링크해 새 회차를
     * 바로 볼 수 있게 한다(회차 뷰어는 seriesId+episodeNo 둘 다 필요해 단일 targetId로 직행 불가).
     */
    private void notifySubscribers(Series series, Episode episode) {
        Long authorId = series.getAuthor().getId();
        List<Long> recipients = subscriptionRepository.findSubscriberIdsBySeriesId(series.getId()).stream()
                .filter(id -> !id.equals(authorId)).toList();
        notificationService.fanOut(recipients, NotificationType.EPISODE_PUBLISHED, NotificationTargetType.SERIES,
                series.getId(), null, "새 회차",
                "구독작 '" + series.getTitle() + "' " + episode.getEpisodeNo() + "화가 올라왔어요.",
                rid -> "EP_PUB:" + episode.getId());
    }

    public SliceResponse<EpisodeSummaryResponse> getEpisodes(Long seriesId, Long viewerId, boolean isAdmin,
                                                             Pageable pageable) {
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        verifyVisibleAccess(series, viewerId, isAdmin);
        verifyAgeAccess(series, viewerId);
        boolean canPreview = isAdmin || series.isAuthoredBy(viewerId);
        Instant now = Instant.now();
        // 목록은 PUBLISHED만 조회 → publishAt non-null 보장(쿼리를 PUBLISHED+SCHEDULED로 바꾸면 evaluate 재검토 필요).
        // 최신 회차부터 내림차순(프론트 회차 목록 기본이 '최신화부터' — 새 회차/UP가 최상단).
        Slice<EpisodeSummaryResponse> slice = episodeRepository
                .findBySeriesIdAndStatusOrderByEpisodeNoDesc(seriesId, EpisodeStatus.PUBLISHED, pageable)
                .map(ep -> EpisodeSummaryResponse.of(ep, episodeAccessEvaluator.evaluate(series, ep, viewerId, canPreview, now)));
        return SliceResponse.from(slice);
    }

    @Transactional
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
        // 엔타이틀먼트 가드(수익화 0단계: 기다리면무료 시간잠금). 잠기면 이미지·조회수 없이 락정보만 반환.
        AccessResult access = episodeAccessEvaluator.evaluate(series, episode, viewerId, canPreview, Instant.now());
        if (!access.accessible()) {
            return EpisodeDetailResponse.locked(episode, access);
        }
        episode.increaseViewCount(); // 더티 체킹으로 트랜잭션 종료 시 UPDATE — 접근 가능 시에만
        List<EpisodeImageResponse> images = episodeImageRepository.findByEpisodeIdOrderBySortOrderAsc(episode.getId()).stream()
                .map(image -> EpisodeImageResponse.of(image, imageStorageService.urlFor(image.getPath())))
                .toList();
        long likeCount = episodeLikeRepository.countByEpisodeId(episode.getId());
        boolean liked = episodeLikeRepository.existsByUserIdAndEpisodeId(viewerId, episode.getId());
        long commentCount = commentRepository.countByEpisodeId(episode.getId());
        return EpisodeDetailResponse.of(episode, images, likeCount, liked, commentCount, access);
    }

    @Transactional
    public void like(Long userId, Long seriesId, int episodeNo) {
        Episode episode = episodeRepository.findBySeriesIdAndEpisodeNo(seriesId, episodeNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        seriesAccessChecker.verifyInteractable(episode.getSeries(), userId);
        if (episodeLikeRepository.existsByUserIdAndEpisodeId(userId, episode.getId())) {
            return; // 멱등: 이미 좋아요면 그대로
        }
        User user = userRepository.getReferenceById(userId);
        episodeLikeRepository.save(EpisodeLike.create(user, episode));
    }

    @Transactional
    public void unlike(Long userId, Long seriesId, int episodeNo) {
        Episode episode = episodeRepository.findBySeriesIdAndEpisodeNo(seriesId, episodeNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        episodeLikeRepository.deleteByUserIdAndEpisodeId(userId, episode.getId());
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
