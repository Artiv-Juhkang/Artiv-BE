package com.juhkang.artiv.domain.ontology;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.block.Block;
import com.juhkang.artiv.domain.block.BlockRepository;
import com.juhkang.artiv.domain.episode.Episode;
import com.juhkang.artiv.domain.episode.EpisodeRepository;
import com.juhkang.artiv.domain.episode.EpisodeStatus;
import com.juhkang.artiv.domain.notification.NotificationRepository;
import com.juhkang.artiv.domain.personalization.Subscription;
import com.juhkang.artiv.domain.personalization.SubscriptionRepository;
import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesRepository;
import com.juhkang.artiv.domain.series.SeriesStatus;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

/**
 * NUDGE 수신자 산출 — 발송 능력이 없는 상태에서 "누가 대상인가"만 못박는다.
 *
 * 이 클래스의 마지막 단언은 notificationRepository.count() == 0 이다. 이 슬라이스의 코드는
 * 아무것도 보내지 않으며, 필터 누락은 사고가 아니라 실패한 테스트로 나타나야 한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class NudgeAudienceTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private NudgeService nudgeService;
    @Autowired private UserRepository userRepository;
    @Autowired private SeriesRepository seriesRepository;
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private ReadingEventRepository readingEventRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private BlockRepository blockRepository;
    @Autowired private NotificationRepository notificationRepository;

    private User creator;
    private Series work;
    private Episode episode;
    private final Instant now = Instant.now();
    private int seq = 0;

    @BeforeEach
    void setUp() {
        creator = userRepository.save(User.create("c@t.com", "pw", "작가", Role.CREATOR, ADULT));
        work = seriesRepository.save(Series.create(
                "작품", "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        episode = episodeRepository.save(
                Episode.create(work, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
    }

    /** 독자 1명을 만들고 daysAgo 전에 열람시킨다. subscribe면 구독까지. */
    private User reader(double daysAgo, boolean subscribe) {
        User u = userRepository.save(User.create(
                "r" + (seq++) + "@t.com", "pw", "독자" + seq, Role.READER, ADULT));
        readAt(u.getId(), daysAgo);
        if (subscribe) {
            subscriptionRepository.save(Subscription.create(u, work));
        }
        return u;
    }

    private void readAt(Long userId, double daysAgo) {
        readingEventRepository.save(ReadingEvent.recordAt(
                now.minus(Duration.ofMinutes((long) (daysAgo * 24 * 60))),
                userId, work.getId(), episode.getId(), 1,
                EntryPoint.SUBSCRIPTION, (short) 50, false, 1000, UUID.randomUUID()));
    }

    private List<Long> recipients() {
        return nudgeService.resolveRecipients(work, now);
    }

    @Test
    void 이탈했고_구독중이면_대상이다() {
        User r = reader(40, true);
        assertThat(recipients()).containsExactly(r.getId());
    }

    @Test
    void 이탈했어도_구독하지_않았으면_제외된다() {
        reader(40, false);
        assertThat(recipients()).isEmpty();
    }

    @Test
    void 탈퇴한_독자는_제외된다() {
        User r = reader(40, true);
        r.withdraw();
        userRepository.save(r);
        assertThat(recipients()).isEmpty();
    }

    @Test
    void 작가를_차단한_독자는_제외된다() {
        User r = reader(40, true);
        blockRepository.save(Block.create(r.getId(), creator.getId()));
        assertThat(recipients()).isEmpty();
    }

    @Test
    void 익명_이벤트는_대상이_되지_않는다() {
        readingEventRepository.save(ReadingEvent.recordAt(
                now.minus(Duration.ofDays(40)), null, work.getId(), episode.getId(), 1,
                EntryPoint.DISCOVER, (short) 50, false, 1000, UUID.randomUUID()));
        assertThat(recipients()).isEmpty();
    }

    @Test
    void 최근에_읽은_구독자는_이탈이_아니다() {
        reader(10, true);
        assertThat(recipients()).isEmpty();
    }

    @Test
    void 작가_본인은_구독중이어도_제외된다() {
        readAt(creator.getId(), 40);
        subscriptionRepository.save(Subscription.create(creator, work));
        assertThat(recipients()).isEmpty();
    }

    /**
     * 화면(classify)과 발송 대상 쿼리가 같은 경계를 쓰는지. 두 정의가 갈라지면
     * 30.0~31.0일 구간 독자가 화면에서는 AT_RISK인데 발송 대상에는 들어간다.
     */
    @Test
    void 이탈_경계가_화면과_발송에서_일치한다() {
        User justInside = reader(30.5, true);   // 아직 이탈 아님
        User justOutside = reader(31.5, true);  // 이탈

        assertThat(AudienceSegment.isLapsed(now.minus(Duration.ofMinutes(30L * 24 * 60 + 720)), now)).isFalse();
        assertThat(AudienceSegment.isLapsed(now.minus(Duration.ofMinutes(31L * 24 * 60 + 720)), now)).isTrue();

        assertThat(recipients())
                .contains(justOutside.getId())
                .doesNotContain(justInside.getId());
    }

    @Test
    void 이탈_20명_중_구독자_3명이면_대상은_3명이다() {
        List<Long> subscribed = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            User u = reader(40, i < 3);
            if (i < 3) {
                subscribed.add(u.getId());
            }
        }
        assertThat(recipients()).containsExactlyInAnyOrderElementsOf(subscribed);
    }

    @Test
    void 이_슬라이스의_코드는_아무것도_보내지_않는다() {
        reader(40, true);
        reader(40, true);
        recipients();

        assertThat(notificationRepository.count()).isZero();
    }
}
