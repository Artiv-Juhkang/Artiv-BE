package com.juhkang.apptoon.domain.series;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.apptoon.TestcontainersConfiguration;
import com.juhkang.apptoon.domain.auth.JwtProvider;
import com.juhkang.apptoon.domain.episode.Episode;
import com.juhkang.apptoon.domain.episode.EpisodeRepository;
import com.juhkang.apptoon.domain.episode.EpisodeStatus;
import com.juhkang.apptoon.domain.personalization.Subscription;
import com.juhkang.apptoon.domain.personalization.SubscriptionRepository;
import com.juhkang.apptoon.domain.user.Role;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;

/** 작품 상세에 발행회차수·최신회차번호·구독여부를 동봉해 프론트 1요청으로 화면을 그리게 한다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeriesDetailTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private EpisodeRepository episodeRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private JwtProvider jwtProvider;

    private String subscriberToken;
    private String nonSubscriberToken;
    private Long seriesId;

    @BeforeEach
    void setUp() {
        User author = userRepository.save(User.create("author@t.com", "pw", "작가", Role.CREATOR, ADULT));
        User subscriber = userRepository.save(User.create("sub@t.com", "pw", "구독자", Role.READER, ADULT));
        User nonSubscriber = userRepository.save(User.create("non@t.com", "pw", "비구독자", Role.READER, ADULT));
        subscriberToken = jwtProvider.createAccessToken(subscriber.getId(), Role.READER);
        nonSubscriberToken = jwtProvider.createAccessToken(nonSubscriber.getId(), Role.READER);

        Series series = seriesRepository.save(Series.create(
                "작품", "설명", author, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        seriesId = series.getId();
        // 발행 2화 + 예약 1화 → episodeCount=2, latestEpisodeNo=2 (발행분 기준)
        episodeRepository.save(Episode.create(series, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
        episodeRepository.save(Episode.create(series, 2, "2화", EpisodeStatus.PUBLISHED, Instant.now()));
        episodeRepository.save(Episode.create(series, 3, "예약화", EpisodeStatus.SCHEDULED, Instant.now().plusSeconds(3600)));

        subscriptionRepository.save(Subscription.create(subscriber, series));
    }

    @Test
    void 상세에_발행회차수와_최신회차번호를_동봉한다() throws Exception {
        mockMvc.perform(get("/api/series/" + seriesId).header("Authorization", "Bearer " + nonSubscriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episodeCount").value(2))
                .andExpect(jsonPath("$.latestEpisodeNo").value(2))
                .andExpect(jsonPath("$.isSubscribed").value(false));
    }

    @Test
    void 구독자에게는_isSubscribed가_true다() throws Exception {
        mockMvc.perform(get("/api/series/" + seriesId).header("Authorization", "Bearer " + subscriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSubscribed").value(true));
    }
}
