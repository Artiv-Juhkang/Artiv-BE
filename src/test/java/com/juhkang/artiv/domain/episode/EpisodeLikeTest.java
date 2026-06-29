package com.juhkang.artiv.domain.episode;

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

/** 회차 좋아요 — 멱등 토글 + 좋아요 수·내 좋아요 여부를 상세에 노출. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EpisodeLikeTest {

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
    private JwtProvider jwtProvider;

    private String readerToken;
    private Long seriesId;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(User.create("creator@t.com", "pw", "작가", Role.CREATOR, ADULT));
        User reader = userRepository.save(User.create("reader@t.com", "pw", "독자", Role.READER, ADULT));
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);

        Series series = seriesRepository.save(Series.create(
                "작품", "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        seriesId = series.getId();
        episodeRepository.save(Episode.create(series, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
    }

    private void assertLike(int count, boolean liked) throws Exception {
        mockMvc.perform(get("/api/series/" + seriesId + "/episodes/1")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(count))
                .andExpect(jsonPath("$.liked").value(liked));
    }

    @Test
    void 좋아요는_멱등_토글이고_좋아요수와_내여부가_상세에_노출된다() throws Exception {
        assertLike(0, false);

        mockMvc.perform(post("/api/series/" + seriesId + "/episodes/1/like")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isCreated());
        assertLike(1, true);

        // 중복 좋아요는 멱등 — 카운트 그대로
        mockMvc.perform(post("/api/series/" + seriesId + "/episodes/1/like")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isCreated());
        assertLike(1, true);

        mockMvc.perform(delete("/api/series/" + seriesId + "/episodes/1/like")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isNoContent());
        assertLike(0, false);
    }
}
