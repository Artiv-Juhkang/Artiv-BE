package com.juhkang.artiv.domain.ontology;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.domain.episode.Episode;
import com.juhkang.artiv.domain.episode.EpisodeRepository;
import com.juhkang.artiv.domain.episode.access.EpisodeAccessGuard;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 열람 계측 수집.
 *
 * 컨트롤러가 아니라 서비스에 두는 이유: series를 LAZY 프록시에서 꺼내 가드에 넘겨야 하는데
 * OSIV가 꺼져 있어 트랜잭션 경계가 필요하다.
 */
@Service
@RequiredArgsConstructor
public class ReadingEventService {

    private final EpisodeRepository episodeRepository;
    private final ReadingEventRepository readingEventRepository;
    private final EpisodeAccessGuard episodeAccessGuard;

    @Transactional
    public void record(Long userId, Long seriesId, int episodeNo, EntryPoint entryPoint,
                       short progressPct, boolean completed, int dwellMs, UUID sessionId) {
        Episode episode = episodeRepository.findBySeriesIdAndEpisodeNo(seriesId, episodeNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        Series series = episode.getSeries();

        // 좋아요·북마크·댓글과 동일한 가드를 통과시킨다(EpisodeService:179 · CommentService:44).
        // 비공개→404 · 19금→403 · 미발행→404 · 잠김→403. 계측이 fire-and-forget이라는 건
        // 클라이언트가 응답을 무시한다는 뜻이지 서버가 검증을 생략해도 된다는 뜻이 아니다.
        // 이 가드가 없으면 202/404 차이로 미공개 회차의 존재가 열거된다(존재 은닉 붕괴).
        episodeAccessGuard.verifyInteractable(series, episode, userId);

        // 작가 본인 열람은 계측하지 않는다. 작가는 정의상 이탈하지 않아 잔존 곡선을 평탄화시키고,
        // 세그먼트 k-익명성 게이트(MIN_SEGMENT_SIZE=5)를 스스로 채워 실독자 수를 역산 가능하게 만든다.
        if (series.isAuthoredBy(userId)) {
            return;
        }

        readingEventRepository.save(ReadingEvent.record(
                userId, seriesId, episode.getId(), episodeNo,
                entryPoint, progressPct, completed, dwellMs, sessionId));
    }
}
