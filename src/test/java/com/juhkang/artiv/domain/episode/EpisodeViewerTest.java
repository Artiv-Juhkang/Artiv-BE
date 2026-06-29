package com.juhkang.artiv.domain.episode;

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
import org.springframework.security.crypto.password.PasswordEncoder;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EpisodeViewerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private EpisodeRepository episodeRepository;
    @Autowired
    private EpisodeImageRepository episodeImageRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtProvider jwtProvider;

    private String adultToken;
    private String minorToken;
    private Long allSeriesId;
    private Long adultSeriesId;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(
                User.create("creator@test.com", passwordEncoder.encode("password123"), "작가", Role.CREATOR, LocalDate.of(1990, 1, 1)));
        User adult = userRepository.save(
                User.create("adult@test.com", passwordEncoder.encode("password123"), "성인", Role.READER, LocalDate.now().minusYears(20)));
        User minor = userRepository.save(
                User.create("minor@test.com", passwordEncoder.encode("password123"), "미성년", Role.READER, LocalDate.now().minusYears(15)));
        adultToken = jwtProvider.createAccessToken(adult.getId(), Role.READER);
        minorToken = jwtProvider.createAccessToken(minor.getId(), Role.READER);

        Series all = seriesRepository.save(Series.create(
                "전체작", "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        Series r19 = seriesRepository.save(Series.create(
                "19금작", "", creator, AgeRating.AGE_19, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        allSeriesId = all.getId();
        adultSeriesId = r19.getId();

        // 전체이용가 작품: 발행 회차 3개(각 이미지 1장)
        for (int no = 1; no <= 3; no++) {
            Episode e = episodeRepository.save(
                    Episode.create(all, no, no + "화", EpisodeStatus.PUBLISHED, Instant.now()));
            episodeImageRepository.save(EpisodeImage.create(e, 0, all.getId() + "/" + no + "/0.png", 800, 1200));
        }
        // 19금 작품: 발행 회차 1개
        Episode e = episodeRepository.save(
                Episode.create(r19, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
        episodeImageRepository.save(EpisodeImage.create(e, 0, r19.getId() + "/1/0.png", 800, 1200));
    }

    @Test
    void 무한스크롤_슬라이스로_발행회차_목록을_조회한다() throws Exception {
        mockMvc.perform(get("/api/series/" + allSeriesId + "/episodes").param("size", "2")
                        .header("Authorization", "Bearer " + adultToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.content[0].episodeNo").value(1));
    }

    @Test
    void 상세는_이미지를_url과_크기와_함께_내려준다() throws Exception {
        mockMvc.perform(get("/api/series/" + allSeriesId + "/episodes/1")
                        .header("Authorization", "Bearer " + adultToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].url").exists())
                .andExpect(jsonPath("$.images[0].width").value(800))
                .andExpect(jsonPath("$.images[0].height").value(1200));
    }

    @Test
    void 미성년자는_19금_작품_회차를_조회할_수_없다_403() throws Exception {
        mockMvc.perform(get("/api/series/" + adultSeriesId + "/episodes")
                        .header("Authorization", "Bearer " + minorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADULT_ONLY"));

        mockMvc.perform(get("/api/series/" + adultSeriesId + "/episodes/1")
                        .header("Authorization", "Bearer " + minorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void 성인은_19금_작품_회차를_조회할_수_있다() throws Exception {
        mockMvc.perform(get("/api/series/" + adultSeriesId + "/episodes/1")
                        .header("Authorization", "Bearer " + adultToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episodeNo").value(1));
    }
}
