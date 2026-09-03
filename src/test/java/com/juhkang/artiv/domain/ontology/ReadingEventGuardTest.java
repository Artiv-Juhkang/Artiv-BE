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

/**
 * 계측 수집의 접근 가드 — 좋아요·북마크·댓글과 같은 규칙을 통과해야 한다.
 *
 * 가드가 없으면 202/404 차이만으로 미공개 회차의 존재가 열거된다(존재 은닉 붕괴).
 * 2026-09-03 리뷰에서 이 경로만 가드를 건너뛰고 있던 것이 발견됐다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReadingEventGuardTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SeriesRepository seriesRepository;
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private ReadingEventRepository readingEventRepository;
    @Autowired private JwtProvider jwtProvider;

    private User creator;
    private String readerToken;
    private String minorToken;
    private String creatorToken;

    @BeforeEach
    void setUp() {
        creator = userRepository.save(User.create("c@t.com", "pw", "작가", Role.CREATOR, ADULT));
        User reader = userRepository.save(User.create("r@t.com", "pw", "독자", Role.READER, ADULT));
        User minor = userRepository.save(
                User.create("m@t.com", "pw", "미성년", Role.READER, LocalDate.now().minusYears(15)));
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);
        minorToken = jwtProvider.createAccessToken(minor.getId(), Role.READER);
        creatorToken = jwtProvider.createAccessToken(creator.getId(), Role.CREATOR);
    }

    private Series series(AgeRating rating, boolean visible) {
        Series s = seriesRepository.save(Series.create(
                "작품", "", creator, rating, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY),
                rating == AgeRating.AGE_19));
        s.changeVisibility(visible);
        return s;
    }

    private String body(Long seriesId, int episodeNo) {
        return """
                {"seriesId":%d,"episodeNo":%d,"entryPoint":"DIRECT","progressPct":50,
                 "completed":false,"dwellMs":1000,"sessionId":"%s"}
                """.formatted(seriesId, episodeNo, UUID.randomUUID());
    }

    private void send(Series s, int no, String token, int expected) throws Exception {
        mockMvc.perform(post("/api/reading-events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(s.getId(), no)))
                .andExpect(status().is(expected));
    }

    @Test
    void 비공개_작품에는_계측할_수_없고_존재를_숨긴다() throws Exception {
        Series s = series(AgeRating.ALL, false);
        episodeRepository.save(Episode.create(s, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));

        send(s, 1, readerToken, 404);
        assertThat(readingEventRepository.count()).isZero();
    }

    @Test
    void 미발행_회차에는_계측할_수_없다() throws Exception {
        Series s = series(AgeRating.ALL, true);
        episodeRepository.save(Episode.create(
                s, 1, "1화", EpisodeStatus.SCHEDULED, Instant.now().plusSeconds(86_400)));

        send(s, 1, readerToken, 404);
        assertThat(readingEventRepository.count()).isZero();
    }

    @Test
    void 미성년은_19금_작품에_계측할_수_없다() throws Exception {
        Series s = series(AgeRating.AGE_19, true);
        episodeRepository.save(Episode.create(s, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));

        send(s, 1, minorToken, 403);
        assertThat(readingEventRepository.count()).isZero();
    }

    @Test
    void 작가_본인_열람은_계측하지_않는다() throws Exception {
        Series s = series(AgeRating.ALL, true);
        episodeRepository.save(Episode.create(s, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));

        send(s, 1, creatorToken, 202);   // 거부가 아니라 조용히 무시
        assertThat(readingEventRepository.count()).isZero();

        send(s, 1, readerToken, 202);    // 독자는 정상 계측
        assertThat(readingEventRepository.count()).isEqualTo(1);
    }
}
