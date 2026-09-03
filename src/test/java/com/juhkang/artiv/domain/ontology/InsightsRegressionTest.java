package com.juhkang.artiv.domain.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Duration;
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
 * 2026-09-03 리뷰에서 드러난 구멍을 막는 회귀 테스트.
 *
 * WorkInsightsTest의 픽스처는 계단형(20,20,20,20,8,8,8,8)이라 어떤 절벽 알고리즘이든 통과시킨다 —
 * 실제로 절대 %p 규칙이 그 테스트를 통과하고도 실데이터에서 47%였다. 여기서는 실제 잔존이 그렇듯
 * **기하급수적으로 감소**하는 곡선에 절벽을 심어, 초기 규칙이었다면 반드시 실패했을 케이스를 만든다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InsightsRegressionTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SeriesRepository seriesRepository;
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private ReadingEventRepository readingEventRepository;
    @Autowired private JwtProvider jwtProvider;

    private User creator;
    private String token;
    private final List<Long> readers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        creator = userRepository.save(User.create("c@t.com", "pw", "작가", Role.CREATOR, ADULT));
        token = jwtProvider.createAccessToken(creator.getId(), Role.CREATOR);
        for (int i = 0; i < 400; i++) {
            readers.add(userRepository.save(
                    User.create("r" + i + "@t.com", "pw", "독자" + i, Role.READER, ADULT)).getId());
        }
    }

    private Series newSeries(String title) {
        return seriesRepository.save(Series.create(
                title, "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
    }

    /** 회차별 도달 독자 수를 그대로 심는다(앞에서부터 n명). occurredAt은 ago일 전. */
    private void seed(Series s, int[] reachPerEpisode, long agoDays) {
        Instant at = Instant.now().minus(Duration.ofDays(agoDays));
        List<ReadingEvent> events = new ArrayList<>();
        for (int i = 0; i < reachPerEpisode.length; i++) {
            int no = i + 1;
            Episode ep = episodeRepository.save(
                    Episode.create(s, no, no + "화", EpisodeStatus.PUBLISHED, Instant.now()));
            for (int r = 0; r < reachPerEpisode[i]; r++) {
                events.add(ReadingEvent.recordAt(at, readers.get(r), s.getId(), ep.getId(), no,
                        EntryPoint.SUBSCRIPTION, (short) 97, true, 60_000, UUID.randomUUID()));
            }
        }
        readingEventRepository.saveAll(events);
    }

    @Test
    void 기하감소_곡선의_후반_절벽을_찾아낸다() throws Exception {
        // 회차당 생존 0.9로 감소하다 20화에서 3분의 1로 붕괴.
        // 20화의 절대 낙폭은 8.0%p(1화 대비) — 초기 규칙(절대 15%p, 이후 5%p)이었다면 놓쳤을 지점이고,
        // 1→2화의 절대 낙폭 30%p가 항상 더 커서 절대 기준은 2화를 가리켰다.
        int[] reach = new int[24];
        double n = 300;
        for (int i = 0; i < 24; i++) {
            if (i == 19) n = n / 3.0;          // 20화 절벽
            else if (i > 0) n = n * 0.9;
            reach[i] = (int) Math.round(n);
        }
        Series s = newSeries("장편");
        seed(s, reach, 1);

        mockMvc.perform(get("/api/ontology/works/" + s.getId() + "/insights")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliff.episodeNo").value(20));
    }

    @Test
    void 절벽이_없는_완만한_곡선에서는_절벽을_보고하지_않는다() throws Exception {
        int[] reach = new int[10];
        double n = 200;
        for (int i = 0; i < 10; i++) {
            if (i > 0) n = n * 0.9;
            reach[i] = (int) Math.round(n);
        }
        Series s = newSeries("완만");
        seed(s, reach, 1);

        mockMvc.perform(get("/api/ontology/works/" + s.getId() + "/insights")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliff").doesNotExist());
    }

    @Test
    void 저표본이면_비율을_노출하지_않는다() throws Exception {
        Series s = newSeries("저표본");
        seed(s, new int[] {3, 2, 2}, 1);   // 총 7세션 < MIN_SAMPLE_FOR_RATE(10)

        mockMvc.perform(get("/api/ontology/works/" + s.getId() + "/insights")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.sessions").value(7))
                .andExpect(jsonPath("$.summary.completionRate").doesNotExist());
    }

    @Test
    void 세그먼트가_5명_미만이면_크기를_숨긴다() throws Exception {
        Series s = newSeries("소수");
        seed(s, new int[] {4, 4, 4}, 1);   // 최근 활동 4명 → NEW 4명(k<5)

        mockMvc.perform(get("/api/ontology/works/" + s.getId() + "/insights")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // disclosed=false면 size는 null로 직렬화된다(필드 자체는 존재).
                .andExpect(jsonPath("$.segments[?(@.segment == 'NEW')].disclosed").value(false))
                .andExpect(jsonPath("$.segments[?(@.segment == 'NEW')].size[0]").doesNotExist());
    }

    @Test
    void 오래_전에만_읽은_독자는_이탈로_분류된다() throws Exception {
        Series s = newSeries("이탈");
        seed(s, new int[] {8, 8, 8}, 60);   // 60일 전에만 열람 → LAPSED 8명

        mockMvc.perform(get("/api/ontology/works/" + s.getId() + "/insights")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segments[?(@.segment == 'LAPSED')].size").value(8))
                .andExpect(jsonPath("$.segments[?(@.segment == 'NEW')].disclosed").value(false));
    }

    @Test
    void 오래된_독자가_최근_다시_읽으면_신규가_아니라_지속이다() throws Exception {
        Series s = newSeries("복귀");
        Episode ep = episodeRepository.save(
                Episode.create(s, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
        Instant old = Instant.now().minus(Duration.ofDays(90));
        Instant recent = Instant.now().minus(Duration.ofDays(2));
        List<ReadingEvent> events = new ArrayList<>();
        for (int r = 0; r < 6; r++) {
            events.add(ReadingEvent.recordAt(old, readers.get(r), s.getId(), ep.getId(), 1,
                    EntryPoint.DIRECT, (short) 50, false, 1000, UUID.randomUUID()));
            events.add(ReadingEvent.recordAt(recent, readers.get(r), s.getId(), ep.getId(), 1,
                    EntryPoint.DIRECT, (short) 50, false, 1000, UUID.randomUUID()));
        }
        readingEventRepository.saveAll(events);

        mockMvc.perform(get("/api/ontology/works/" + s.getId() + "/insights")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segments[?(@.segment == 'LOYAL')].size").value(6))
                .andExpect(jsonPath("$.segments[?(@.segment == 'NEW')].disclosed").value(false));
    }

    @Test
    void 탈퇴하면_열람_이벤트가_익명화된다() {
        Series s = newSeries("탈퇴");
        seed(s, new int[] {5, 5}, 1);
        Long victim = readers.get(0);
        assertThat(readingEventRepository.findAll().stream()
                .filter(e -> victim.equals(e.getUserId())).count()).isEqualTo(2);

        readingEventRepository.anonymizeUser(victim);

        assertThat(readingEventRepository.findAll().stream()
                .filter(e -> victim.equals(e.getUserId())).count()).isZero();
        assertThat(readingEventRepository.count()).isEqualTo(10);   // 행은 남는다
    }
}
