package com.juhkang.artiv.domain.episode;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.auth.JwtProvider;
import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesRepository;
import com.juhkang.artiv.domain.series.SeriesStatus;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

/**
 * 좋아요·읽음·북마크에도 회차 상세와 동일한 접근 가드를 적용한다 —
 * episodeNo만 알면 비공개·성인 작품에 상호작용하던 갭 차단.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InteractionGuardTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);
    private static final LocalDate MINOR = LocalDate.now().minusYears(15);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private EpisodeRepository episodeRepository;
    @Autowired
    private JwtProvider jwtProvider;

    private String minorToken;
    private String otherToken;
    private Long adultSeriesId;
    private Long hiddenSeriesId;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(User.create("creator@t.com", "pw", "작가", Role.CREATOR, ADULT));
        User minor = userRepository.save(User.create("minor@t.com", "pw", "미성년", Role.READER, MINOR));
        User other = userRepository.save(User.create("other@t.com", "pw", "타인", Role.READER, ADULT));
        minorToken = jwtProvider.createAccessToken(minor.getId(), Role.READER);
        otherToken = jwtProvider.createAccessToken(other.getId(), Role.READER);

        Series adult = seriesRepository.save(Series.create(
                "19금작", "", creator, AgeRating.AGE_19, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        adultSeriesId = adult.getId();
        episodeRepository.save(Episode.create(adult, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));

        Series hidden = Series.create(
                "비공개작", "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY));
        hidden.changeVisibility(false);
        hiddenSeriesId = seriesRepository.save(hidden).getId();
        episodeRepository.save(Episode.create(hidden, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
    }

    @Test
    void 미성년은_19금_회차에_좋아요할_수_없다_403() throws Exception {
        mockMvc.perform(post("/api/series/" + adultSeriesId + "/episodes/1/like")
                        .header("Authorization", "Bearer " + minorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADULT_ONLY"));
    }

    @Test
    void 미성년은_19금_회차를_읽음처리할_수_없다_403() throws Exception {
        mockMvc.perform(post("/api/series/" + adultSeriesId + "/episodes/1/read")
                        .header("Authorization", "Bearer " + minorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADULT_ONLY"));
    }

    @Test
    void 미성년은_19금_회차를_북마크할_수_없다_403() throws Exception {
        mockMvc.perform(post("/api/series/" + adultSeriesId + "/episodes/1/bookmark")
                        .header("Authorization", "Bearer " + minorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADULT_ONLY"));
    }

    @Test
    void 타인은_비공개작품_회차에_좋아요할_수_없다_404() throws Exception {
        mockMvc.perform(post("/api/series/" + hiddenSeriesId + "/episodes/1/like")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }
}
