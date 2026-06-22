package com.juhkang.apptoon.domain.episode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.apptoon.TestcontainersConfiguration;
import com.juhkang.apptoon.domain.auth.JwtProvider;
import com.juhkang.apptoon.domain.personalization.ReadLogRepository;
import com.juhkang.apptoon.domain.series.AgeRating;
import com.juhkang.apptoon.domain.series.ReleasePolicy;
import com.juhkang.apptoon.domain.series.Series;
import com.juhkang.apptoon.domain.series.SeriesRepository;
import com.juhkang.apptoon.domain.series.SeriesStatus;
import com.juhkang.apptoon.domain.user.Role;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MonetizationFlowTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private SeriesRepository seriesRepository;
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private ReadLogRepository readLogRepository;

    private Long authorId;
    private String authorToken;
    private Long readerId;
    private String readerToken;
    private Series series;

    private User user(String nickname, Role role) {
        return userRepository.save(User.create(nickname + "@mon.com", passwordEncoder.encode("password123"), nickname, role, ADULT));
    }

    private Episode episode(int no, Instant publishAt) {
        return episodeRepository.save(Episode.create(series, no, no + "화", EpisodeStatus.PUBLISHED, publishAt));
    }

    private String detailUrl(int no) {
        return "/api/series/" + series.getId() + "/episodes/" + no;
    }

    @BeforeEach
    void setUp() {
        User author = user("수익작가", Role.CREATOR);
        authorId = author.getId();
        authorToken = jwtProvider.createAccessToken(authorId, Role.CREATOR);
        readerId = user("수익독자", Role.READER).getId();
        readerToken = jwtProvider.createAccessToken(readerId, Role.READER);
        series = seriesRepository.save(Series.create("수익작", "설명", author, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
    }

    private void setWaitFree(int days) {
        series.changeReleasePolicy(ReleasePolicy.WAIT_FREE, days);
        seriesRepository.save(series);
    }

    @Test
    void 전체무료_작품은_일반독자가_회차를_연다() throws Exception {
        episode(1, Instant.now());
        mockMvc.perform(get(detailUrl(1)).header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.lockReason").value("NONE"));
    }

    @Test
    void 기다리면무료_최신화는_일반독자에게_잠기고_작가는_프리뷰한다() throws Exception {
        Episode latest = episode(1, Instant.now().minus(Duration.ofDays(1))); // freeAt = 6일 후
        setWaitFree(7);

        // 일반독자 → 잠김(이미지 없음, freeAt 제공)
        mockMvc.perform(get(detailUrl(1)).header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.lockReason").value("WAIT"))
                .andExpect(jsonPath("$.freeAt").isNotEmpty())
                .andExpect(jsonPath("$.images.length()").value(0));
        // 잠긴 회차 거부 시 조회수 증가 안 함(작가 프리뷰 GET 전에 확인)
        assertThat(episodeRepository.findById(latest.getId()).orElseThrow().getViewCount()).isZero();
        // 작가 본인 → 프리뷰 열림
        mockMvc.perform(get(detailUrl(1)).header("Authorization", "Bearer " + authorToken))
                .andExpect(jsonPath("$.locked").value(false));
    }

    @Test
    void 기다리면무료_무료전환_지난_회차는_일반독자가_연다() throws Exception {
        episode(1, Instant.now().minus(Duration.ofDays(10))); // freeAt = 3일 전(이미 무료)
        setWaitFree(7);
        mockMvc.perform(get(detailUrl(1)).header("Authorization", "Bearer " + readerToken))
                .andExpect(jsonPath("$.locked").value(false));
    }

    @Test
    void 회차_목록은_무료_회차와_잠긴_회차를_구분해_보여준다() throws Exception {
        episode(1, Instant.now().minus(Duration.ofDays(10))); // 무료
        episode(2, Instant.now().minus(Duration.ofDays(1)));  // 잠김
        setWaitFree(7);
        mockMvc.perform(get("/api/series/" + series.getId() + "/episodes").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.episodeNo == 1)].locked").value(false))
                .andExpect(jsonPath("$.content[?(@.episodeNo == 2)].locked").value(true));
    }

    @Test
    void 작가가_공개정책을_바꾼다() throws Exception {
        mockMvc.perform(patch("/api/series/" + series.getId() + "/release-policy")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"WAIT_FREE\",\"waitFreeDays\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("WAIT_FREE"))
                .andExpect(jsonPath("$.waitFreeDays").value(7));
    }

    @Test
    void 비작가는_공개정책을_못_바꾼다_403() throws Exception {
        mockMvc.perform(patch("/api/series/" + series.getId() + "/release-policy")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"WAIT_FREE\",\"waitFreeDays\":7}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 기다리면무료인데_주기가_0이면_400() throws Exception {
        mockMvc.perform(patch("/api/series/" + series.getId() + "/release-policy")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"WAIT_FREE\",\"waitFreeDays\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 잠긴_회차는_읽음처리해도_기록되지_않는다() throws Exception {
        Episode locked = episode(1, Instant.now().minus(Duration.ofDays(1))); // 잠김
        setWaitFree(7);
        mockMvc.perform(post("/api/series/" + series.getId() + "/episodes/1/read").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isCreated()); // 엔드포인트는 201이지만 기록은 no-op
        assertThat(readLogRepository.existsByUserIdAndEpisodeId(readerId, locked.getId())).isFalse();
    }

    @Test
    void 전체무료로_되돌리면_전회차_열리고_주기는_청소된다() throws Exception {
        episode(1, Instant.now().minus(Duration.ofDays(1)));
        setWaitFree(7);
        mockMvc.perform(patch("/api/series/" + series.getId() + "/release-policy")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"FREE_ALL\",\"waitFreeDays\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("FREE_ALL"))
                .andExpect(jsonPath("$.waitFreeDays").value(nullValue()));
        mockMvc.perform(get(detailUrl(1)).header("Authorization", "Bearer " + readerToken))
                .andExpect(jsonPath("$.locked").value(false));
    }
}
