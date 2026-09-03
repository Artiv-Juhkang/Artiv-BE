package com.juhkang.artiv.domain.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Duration;
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
import com.juhkang.artiv.domain.notification.NotificationRepository;
import com.juhkang.artiv.domain.notification.NotificationType;
import com.juhkang.artiv.domain.personalization.Subscription;
import com.juhkang.artiv.domain.personalization.SubscriptionRepository;
import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesRepository;
import com.juhkang.artiv.domain.series.SeriesStatus;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** NUDGE 액션 — 실제 발송 + 가드 + 감사 로그. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NudgeActionTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private NudgeService nudgeService;
    @Autowired private UserRepository userRepository;
    @Autowired private SeriesRepository seriesRepository;
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private ReadingEventRepository readingEventRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private OntologyActionLogRepository logRepository;
    @Autowired private JwtProvider jwtProvider;
    @PersistenceContext private EntityManager em;

    private User creator;
    private Series work;
    private Episode episode;
    private String creatorToken;
    private String strangerToken;
    private String readerToken;
    private int seq = 0;

    @BeforeEach
    void setUp() {
        creator = userRepository.save(User.create("c@t.com", "pw", "작가", Role.CREATOR, ADULT));
        User stranger = userRepository.save(User.create("s@t.com", "pw", "타작가", Role.CREATOR, ADULT));
        User plainReader = userRepository.save(User.create("p@t.com", "pw", "그냥독자", Role.READER, ADULT));
        creatorToken = jwtProvider.createAccessToken(creator.getId(), Role.CREATOR);
        strangerToken = jwtProvider.createAccessToken(stranger.getId(), Role.CREATOR);
        readerToken = jwtProvider.createAccessToken(plainReader.getId(), Role.READER);

        work = seriesRepository.save(Series.create(
                "작품", "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        episode = episodeRepository.save(
                Episode.create(work, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
    }

    private void lapsedSubscribers(int n) {
        for (int i = 0; i < n; i++) {
            User u = userRepository.save(User.create(
                    "r" + (seq++) + "@t.com", "pw", "독자" + seq, Role.READER, ADULT));
            readingEventRepository.save(ReadingEvent.recordAt(
                    Instant.now().minus(Duration.ofDays(40)), u.getId(), work.getId(), episode.getId(), 1,
                    EntryPoint.SUBSCRIPTION, (short) 50, false, 1000, UUID.randomUUID()));
            subscriptionRepository.save(Subscription.create(u, work));
        }
    }

    private org.springframework.test.web.servlet.ResultActions nudge(String token) throws Exception {
        return mockMvc.perform(post("/api/ontology/actions/nudge-lapsed-audience")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seriesId\":%d}".formatted(work.getId())));
    }

    @Test
    void 구독중인_이탈독자_5명에게_발송되고_로그가_남는다() throws Exception {
        lapsedSubscribers(5);

        nudge(creatorToken).andExpect(status().isOk());

        assertThat(notificationRepository.findAll())
                .hasSize(5)
                .allSatisfy(n -> assertThat(n.getType()).isEqualTo(NotificationType.NUDGE));
        assertThat(logRepository.findAll())
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.getResult()).isEqualTo(ActionResult.EXECUTED);
                    assertThat(l.getRecipientCount()).isEqualTo(5);
                    assertThat(l.getObjectType()).isEqualTo(ObjectType.AUDIENCE_SEGMENT);
                });
    }

    @Test
    void 성공_응답에_수신자_수가_없다() throws Exception {
        lapsedSubscribers(5);

        nudge(creatorToken).andExpect(status().isOk()).andExpect(content().string(""));
    }

    @Test
    void 같은_주에_다시_보내면_409로_거부되고_거부도_로그에_남는다() throws Exception {
        lapsedSubscribers(5);
        nudge(creatorToken).andExpect(status().isOk());

        nudge(creatorToken).andExpect(status().isConflict());

        assertThat(notificationRepository.count()).isEqualTo(5);   // 늘지 않는다
        assertThat(logRepository.count()).isEqualTo(2);
        assertThat(logRepository.findAll().stream()
                .filter(l -> l.getResult() == ActionResult.BLOCKED_THROTTLED)).hasSize(1);
    }

    /**
     * 로그 롤백 회귀를 막는 유일한 방어선. 서비스가 거부 케이스에서 예외를 던지기 시작하면
     * 같은 트랜잭션의 BLOCKED 로그가 함께 롤백돼 프로덕션에 기록이 남지 않는다
     * (테스트에서는 rollback-only 마킹만 되어 통과해버린다).
     * 이 단언을 완화하려면 docs/fde/04-permissions.md를 함께 고쳐야 한다.
     */
    @Test
    void 서비스는_거부_케이스에서_예외를_던지지_않고_결과를_반환한다() {
        lapsedSubscribers(5);
        assertThat(nudgeService.execute(work.getId(), creator.getId())).isEqualTo(ActionResult.EXECUTED);
        assertThat(nudgeService.execute(work.getId(), creator.getId()))
                .isEqualTo(ActionResult.BLOCKED_THROTTLED);

        Series other = seriesRepository.save(Series.create(
                "소수작품", "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        assertThat(nudgeService.execute(other.getId(), creator.getId()))
                .isEqualTo(ActionResult.BLOCKED_TOO_SMALL);
    }

    @Test
    void 한_주가_지나면_다시_보낼_수_있다() throws Exception {
        lapsedSubscribers(5);
        nudge(creatorToken).andExpect(status().isOk());

        em.createQuery("update OntologyActionLog l set l.occurredAt = :t")
                .setParameter("t", Instant.now().minus(Duration.ofDays(8)))
                .executeUpdate();
        em.clear();

        nudge(creatorToken).andExpect(status().isOk());
        assertThat(logRepository.findAll().stream()
                .filter(l -> l.getResult() == ActionResult.EXECUTED)).hasSize(2);
    }

    @Test
    void 대상이_5명_미만이면_403이고_아무것도_보내지_않는다() throws Exception {
        lapsedSubscribers(4);

        nudge(creatorToken).andExpect(status().isForbidden());

        assertThat(notificationRepository.count()).isZero();
        assertThat(logRepository.findAll())
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.getResult()).isEqualTo(ActionResult.BLOCKED_TOO_SMALL);
                    assertThat(l.getRecipientCount()).isZero();   // 거부는 규모를 남기지 않는다
                });
    }

    @Test
    void 타인_작품에는_실행할_수_없고_로그도_남지_않는다() throws Exception {
        lapsedSubscribers(5);

        nudge(strangerToken).andExpect(status().isNotFound());

        assertThat(notificationRepository.count()).isZero();
        assertThat(logRepository.count()).isZero();
    }

    /** 루프 ④ — 실행이 진단 화면으로 되돌아온다. */
    @Test
    void 실행_후_진단에_lastAction이_나타난다() throws Exception {
        lapsedSubscribers(5);
        nudge(creatorToken).andExpect(status().isOk());

        mockMvc.perform(get("/api/ontology/works/" + work.getId() + "/insights")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastAction.actionType").value("NUDGE_LAPSED_AUDIENCE"))
                .andExpect(jsonPath("$.lastAction.label").value("이탈 독자 알림"))
                .andExpect(jsonPath("$.lastAction.occurredAt").exists());
    }

    @Test
    void 독자_역할은_실행할_수_없다() throws Exception {
        lapsedSubscribers(5);

        nudge(readerToken).andExpect(status().isForbidden());

        assertThat(notificationRepository.count()).isZero();
    }
}
