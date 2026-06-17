package com.juhkang.apptoon.domain.episode;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 예약 발행 처리. 주기적으로 발행 시각이 지난 SCHEDULED 회차를 PUBLISHED로 전환한다.
 */
@Component
@RequiredArgsConstructor
public class EpisodePublisher {

    private final EpisodeService episodeService;

    @Scheduled(fixedDelayString = "${app.episode.publish-poll-ms:60000}")
    public void publishDue() {
        episodeService.publishDueEpisodes(Instant.now());
    }
}
