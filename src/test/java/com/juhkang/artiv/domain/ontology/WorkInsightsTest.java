package com.juhkang.artiv.domain.ontology;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.auth.JwtProvider;
import com.juhkang.artiv.domain.episode.Episode;
import com.juhkang.artiv.domain.episode.EpisodeRepository;
import com.juhkang.artiv.domain.episode.EpisodeStatus;
import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesRepository;
import com.juhkang.artiv.domain.series.SeriesStatus;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

/**
 * 작품 진단 — 잔존 곡선·절벽 탐지·유입경로·세그먼트.
 *
 * 절벽 시나리오: 1~4화는 20명 전원이 읽고, 5화부터 8명만 읽는다(60%p 급락).
 * 탐지기가 5화를 짚어야 한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkInsightsTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);
    private static final int READERS = 20;
    private static final int AFTER_CLIFF = 8;
    private static final int EPISODES = 8;
    private static final int CLIFF_NO = 5;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SeriesRepository seriesRepository;
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private ReadingEventRepository readingEventRepository;
    @Autowired private JwtProvider jwtProvider;

    private String ownerToken;
    private String strangerToken;
    private Long seriesId;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(User.create("o@t.com", "pw", "주인", Role.CREATOR, ADULT));
        User stranger = userRepository.save(User.create("s@t.com", "pw", "타인", Role.CREATOR, ADULT));
        ownerToken = jwtProvider.createAccessToken(owner.getId(), Role.CREATOR);
        strangerToken = jwtProvider.createAccessToken(stranger.getId(), Role.CREATOR);

        Series series = seriesRepository.save(Series.create(
                "작품", "", owner, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        seriesId = series.getId();

        List<Long> readerIds = new ArrayList<>();
        for (int i = 0; i < READERS; i++) {
            readerIds.add(userRepository.save(
                    User.create("r" + i + "@t.com", "pw", "독자" + i, Role.READER, ADULT)).getId());
        }

        List<ReadingEvent> events = new ArrayList<>();
        for (int no = 1; no <= EPISODES; no++) {
            Episode ep = episodeRepository.save(
                    Episode.create(series, no, no + "화", EpisodeStatus.PUBLISHED, Instant.now()));
            int count = no < CLIFF_NO ? READERS : AFTER_CLIFF;
            for (int i = 0; i < count; i++) {
                events.add(ReadingEvent.record(readerIds.get(i), seriesId, ep.getId(), no,
                        i % 2 == 0 ? EntryPoint.SUBSCRIPTION : EntryPoint.DISCOVER,
                        (short) 97, true, 60_000, UUID.randomUUID()));
            }
        }
        readingEventRepository.saveAll(events);
    }

    @Test
    void 절벽_회차를_짚어낸다() throws Exception {
        mockMvc.perform(get("/api/ontology/works/" + seriesId + "/insights")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliff.episodeNo").value(CLIFF_NO));
    }

    @Test
    void 잔존곡선은_회차수만큼_나오고_1화가_100퍼센트다() throws Exception {
        mockMvc.perform(get("/api/ontology/works/" + seriesId + "/insights")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retention.length()").value(EPISODES))
                .andExpect(jsonPath("$.retention[0].retentionPct").value(100.0))
                .andExpect(jsonPath("$.retention[0].uniqueReaders").value(READERS));
    }

    @Test
    void 유입경로가_두_종류로_집계된다() throws Exception {
        mockMvc.perform(get("/api/ontology/works/" + seriesId + "/insights")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entryPoints.length()").value(2));
    }

    @Test
    void 타인_작품_진단은_404다() throws Exception {
        mockMvc.perform(get("/api/ontology/works/" + seriesId + "/insights")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 액션_이력이_없으면_lastAction이_null이다() throws Exception {
        mockMvc.perform(get("/api/ontology/works/" + seriesId + "/insights")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastAction").doesNotExist());
    }

    @Test
    void 적용가능_액션이_함께_내려온다() throws Exception {
        mockMvc.perform(get("/api/ontology/works/" + seriesId + "/insights")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicableActions.length()").value(3));
    }
}
