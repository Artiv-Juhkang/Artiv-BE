package com.juhkang.artiv.domain.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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

/** 계측 수집 — read_logs와 달리 재열람이 별개 행으로 쌓인다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReadingEventTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SeriesRepository seriesRepository;
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private ReadingEventRepository readingEventRepository;
    @Autowired private JwtProvider jwtProvider;

    private String readerToken;
    private Long seriesId;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(User.create("c@t.com", "pw", "작가", Role.CREATOR, ADULT));
        User reader = userRepository.save(User.create("r@t.com", "pw", "독자", Role.READER, ADULT));
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);

        Series series = seriesRepository.save(Series.create(
                "작품", "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        seriesId = series.getId();
        episodeRepository.save(Episode.create(series, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
    }

    private void send(int progress, boolean completed) throws Exception {
        String body = """
                {"seriesId":%d,"episodeNo":1,"entryPoint":"DISCOVER","progressPct":%d,
                 "completed":%b,"dwellMs":42000,"sessionId":"%s"}
                """.formatted(seriesId, progress, completed, UUID.randomUUID());
        mockMvc.perform(post("/api/reading-events")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
    }

    @Test
    void 같은_유저가_같은_회차를_두_번_읽으면_두_행이_쌓인다() throws Exception {
        send(40, false);
        send(100, true);

        assertThat(readingEventRepository.count()).isEqualTo(2);
    }

    @Test
    void 진도율이_범위를_벗어나면_400이다() throws Exception {
        String body = """
                {"seriesId":%d,"episodeNo":1,"entryPoint":"DISCOVER","progressPct":101,
                 "completed":false,"dwellMs":1,"sessionId":"%s"}
                """.formatted(seriesId, UUID.randomUUID());
        mockMvc.perform(post("/api/reading-events")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_회차면_404다() throws Exception {
        String body = """
                {"seriesId":%d,"episodeNo":99,"entryPoint":"DISCOVER","progressPct":10,
                 "completed":false,"dwellMs":1,"sessionId":"%s"}
                """.formatted(seriesId, UUID.randomUUID());
        mockMvc.perform(post("/api/reading-events")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }
}
