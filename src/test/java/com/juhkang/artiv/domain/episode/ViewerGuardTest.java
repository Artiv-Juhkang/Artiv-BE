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
 * 목록에서 빠지는 비공개(visible=false) 작품·미발행(SCHEDULED) 회차가
 * ID 직접접근으로도 노출되지 않는지, 그리고 프리뷰(작가 본인/ADMIN)는 허용되는지 검증.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ViewerGuardTest {

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

    private String adminToken;
    private String authorToken;
    private String readerToken;
    private Long hiddenSeriesId;
    private Long publicSeriesId;

    @BeforeEach
    void setUp() {
        User admin = userRepository.save(User.create("admin@t.com", "pw", "관리자", Role.ADMIN, ADULT));
        User author = userRepository.save(User.create("author@t.com", "pw", "작가", Role.CREATOR, ADULT));
        User reader = userRepository.save(User.create("reader@t.com", "pw", "독자", Role.READER, ADULT));
        adminToken = jwtProvider.createAccessToken(admin.getId(), Role.ADMIN);
        authorToken = jwtProvider.createAccessToken(author.getId(), Role.CREATOR);
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);

        Series hidden = Series.create("비공개작", "", author, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY));
        hidden.changeVisibility(false);
        hiddenSeriesId = seriesRepository.save(hidden).getId();

        Series pub = seriesRepository.save(Series.create(
                "공개작", "", author, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        publicSeriesId = pub.getId();
        episodeRepository.save(Episode.create(pub, 1, "예약화", EpisodeStatus.SCHEDULED, Instant.now().plusSeconds(3600)));
    }

    private void getDetail(Long seriesId, String token, int expectedStatus) throws Exception {
        mockMvc.perform(get("/api/series/" + seriesId).header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void 비공개작품_상세는_타인에게_404() throws Exception {
        getDetail(hiddenSeriesId, readerToken, 404);
    }

    @Test
    void 비공개작품_상세는_작가본인에게_200() throws Exception {
        getDetail(hiddenSeriesId, authorToken, 200);
    }

    @Test
    void 비공개작품_상세는_ADMIN에게_200() throws Exception {
        getDetail(hiddenSeriesId, adminToken, 200);
    }

    @Test
    void 비공개작품_회차목록은_타인에게_404() throws Exception {
        mockMvc.perform(get("/api/series/" + hiddenSeriesId + "/episodes")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 미발행회차_상세는_타인에게_404() throws Exception {
        mockMvc.perform(get("/api/series/" + publicSeriesId + "/episodes/1")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 미발행회차_상세는_작가본인에게_200_SCHEDULED() throws Exception {
        mockMvc.perform(get("/api/series/" + publicSeriesId + "/episodes/1")
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }
}
