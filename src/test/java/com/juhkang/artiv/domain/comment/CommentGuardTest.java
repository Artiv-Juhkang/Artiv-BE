package com.juhkang.artiv.domain.comment;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.auth.JwtProvider;
import com.juhkang.artiv.domain.episode.Episode;
import com.juhkang.artiv.domain.episode.EpisodeRepository;
import com.juhkang.artiv.domain.episode.EpisodeStatus;
import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.ReleasePolicy;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesRepository;
import com.juhkang.artiv.domain.series.SeriesStatus;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

/**
 * 회차 댓글에도 회차 상세와 동일한 접근 가드를 적용한다(F5) —
 * episodeNo만 알면 비공개·19금·미발행·잠긴 회차의 댓글을 읽고 쓸 수 있던 갭 차단.
 * 좋아요·삭제는 경로의 seriesId·episodeNo 소속도 검증한다(F17).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentGuardTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);
    private static final LocalDate MINOR = LocalDate.now().minusYears(15);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SeriesRepository seriesRepository;
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private JwtProvider jwtProvider;

    private String creatorToken;
    private String minorToken;
    private String otherToken;
    private Long adultSeriesId;
    private Long hiddenSeriesId;
    private Long lockedSeriesId;
    private Long normalSeriesId;   // FREE_ALL: ep1 PUBLISHED + ep2 SCHEDULED
    private Long normal2SeriesId;  // FREE_ALL: ep1 PUBLISHED (경로 불일치 검증용)

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(User.create("creator@t.com", "pw", "작가", Role.CREATOR, ADULT));
        User minor = userRepository.save(User.create("minor@t.com", "pw", "미성년", Role.READER, MINOR));
        User other = userRepository.save(User.create("other@t.com", "pw", "타인", Role.READER, ADULT));
        creatorToken = jwtProvider.createAccessToken(creator.getId(), Role.CREATOR);
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

        Series locked = Series.create(
                "기다리면무료작", "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY));
        locked.changeReleasePolicy(ReleasePolicy.WAIT_FREE, 7);
        lockedSeriesId = seriesRepository.save(locked).getId();
        episodeRepository.save(Episode.create(locked, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now())); // 방금 발행 → 7일 잠김

        Series normal = seriesRepository.save(Series.create(
                "공개작", "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        normalSeriesId = normal.getId();
        episodeRepository.save(Episode.create(normal, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
        episodeRepository.save(Episode.create(normal, 2, "2화", EpisodeStatus.SCHEDULED,
                Instant.now().plusSeconds(86_400)));

        Series normal2 = seriesRepository.save(Series.create(
                "공개작2", "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        normal2SeriesId = normal2.getId();
        episodeRepository.save(Episode.create(normal2, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
    }

    private String commentsPath(Long seriesId, int episodeNo) {
        return "/api/series/" + seriesId + "/episodes/" + episodeNo + "/comments";
    }

    private long writeComment(String token, Long seriesId, int episodeNo) throws Exception {
        String body = mockMvc.perform(post(commentsPath(seriesId, episodeNo))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"댓글\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void 타인은_비공개작품_회차_댓글을_읽거나_쓸_수_없다_404() throws Exception {
        mockMvc.perform(get(commentsPath(hiddenSeriesId, 1)).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(commentsPath(hiddenSeriesId, 1)).header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"몰래 댓글\"}"))
                .andExpect(status().isNotFound());
        // 작가 본인은 허용
        mockMvc.perform(get(commentsPath(hiddenSeriesId, 1)).header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk());
    }

    @Test
    void 미성년은_19금_회차_댓글을_읽거나_쓸_수_없다_403() throws Exception {
        mockMvc.perform(get(commentsPath(adultSeriesId, 1)).header("Authorization", "Bearer " + minorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADULT_ONLY"));
        mockMvc.perform(post(commentsPath(adultSeriesId, 1)).header("Authorization", "Bearer " + minorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"몰래 댓글\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 미발행_회차_댓글은_타인에게_404() throws Exception {
        mockMvc.perform(get(commentsPath(normalSeriesId, 2)).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(commentsPath(normalSeriesId, 2)).header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"미리 댓글\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 잠긴_기다리면무료_회차_댓글은_403() throws Exception {
        mockMvc.perform(get(commentsPath(lockedSeriesId, 1)).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post(commentsPath(lockedSeriesId, 1)).header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"잠김 댓글\"}"))
                .andExpect(status().isForbidden());
        // 작가 본인은 잠김과 무관하게 허용
        mockMvc.perform(get(commentsPath(lockedSeriesId, 1)).header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk());
    }

    @Test
    void 댓글_좋아요와_삭제는_경로의_회차_소속을_검증한다_404() throws Exception {
        long commentId = writeComment(otherToken, normalSeriesId, 1);

        // 다른 작품 경로로 좋아요/삭제 시도 → 404
        mockMvc.perform(post(commentsPath(normal2SeriesId, 1) + "/" + commentId + "/like")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(commentsPath(normal2SeriesId, 1) + "/" + commentId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        // 올바른 경로는 정상 동작
        mockMvc.perform(post(commentsPath(normalSeriesId, 1) + "/" + commentId + "/like")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isCreated());
        mockMvc.perform(delete(commentsPath(normalSeriesId, 1) + "/" + commentId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void 댓글_목록은_엉뚱한_sort_파라미터를_무시한다() throws Exception {
        writeComment(otherToken, normalSeriesId, 1);
        mockMvc.perform(get(commentsPath(normalSeriesId, 1)).param("sort", "bogus")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());
    }
}
