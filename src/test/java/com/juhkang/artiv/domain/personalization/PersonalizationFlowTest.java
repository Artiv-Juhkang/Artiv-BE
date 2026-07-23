package com.juhkang.artiv.domain.personalization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PersonalizationFlowTest {

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
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtProvider jwtProvider;

    private String readerToken;
    private Series series;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(
                User.create("creator@test.com", passwordEncoder.encode("password123"), "작가", Role.CREATOR, ADULT));
        User reader = userRepository.save(
                User.create("reader@test.com", passwordEncoder.encode("password123"), "독자", Role.READER, ADULT));
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);

        series = seriesRepository.save(Series.create(
                "작품", "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        series.changeCover("/files/cover-p.png");
        seriesRepository.save(series);
        episodeRepository.save(Episode.create(series, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
    }

    @Test
    void 구독후_새회차_발행되면_UP_읽으면_사라진다() throws Exception {
        long sid = series.getId();

        // 구독
        mockMvc.perform(post("/api/series/" + sid + "/subscription")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isCreated());

        // 1화 미열람 → UP (Page 봉투 — read-history와 동일 계약)
        mockMvc.perform(get("/api/me/subscriptions")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].latestEpisodeNo").value(1))
                .andExpect(jsonPath("$.content[0].lastReadEpisodeNo").value(0))
                .andExpect(jsonPath("$.content[0].up").value(true))
                .andExpect(jsonPath("$.content[0].coverUrl").value("/files/cover-p.png"));

        // 1화 읽음 → UP 사라짐
        mockMvc.perform(post("/api/series/" + sid + "/episodes/1/read")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/me/subscriptions")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(jsonPath("$.content[0].lastReadEpisodeNo").value(1))
                .andExpect(jsonPath("$.content[0].up").value(false));

        // 2화 발행 → 다시 UP
        episodeRepository.save(Episode.create(series, 2, "2화", EpisodeStatus.PUBLISHED, Instant.now()));
        mockMvc.perform(get("/api/me/subscriptions")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(jsonPath("$.content[0].latestEpisodeNo").value(2))
                .andExpect(jsonPath("$.content[0].up").value(true));

        // 2화 읽음 → UP 사라짐
        mockMvc.perform(post("/api/series/" + sid + "/episodes/2/read")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/me/subscriptions")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(jsonPath("$.content[0].up").value(false));
    }

    @Test
    void 구독_취소하면_목록에서_사라진다() throws Exception {
        long sid = series.getId();

        mockMvc.perform(post("/api/series/" + sid + "/subscription")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/series/" + sid + "/subscription")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/subscriptions")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
