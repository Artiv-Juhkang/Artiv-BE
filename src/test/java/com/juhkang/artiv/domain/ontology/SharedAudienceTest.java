package com.juhkang.artiv.domain.ontology;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
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
 * sharesAudienceWith 3중 방어 — 방향성 · k-익명성 · 가시성 전파.
 *
 * 순서가 계약이다. 특히 가시성 필터가 상위 N 절단보다 먼저 걸려야 한다 —
 * 절단이 먼저면 숨겨진 작품이 슬롯만 먹어 결손 개수로 그 존재가 샌다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SharedAudienceTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);
    private static final LocalDate MINOR = LocalDate.now().minusYears(15);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SeriesRepository seriesRepository;
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private ReadingEventRepository readingEventRepository;
    @Autowired private JwtProvider jwtProvider;

    private User me;
    private User other;
    private Series mine;
    private final List<Long> readers = new ArrayList<>();
    private int seq = 0;

    @BeforeEach
    void setUp() {
        me = userRepository.save(User.create("me@t.com", "pw", "나", Role.CREATOR, ADULT));
        other = userRepository.save(User.create("o@t.com", "pw", "남", Role.CREATOR, ADULT));
        for (int i = 0; i < 30; i++) {
            readers.add(userRepository.save(
                    User.create("r" + i + "@t.com", "pw", "독자" + i, Role.READER, ADULT)).getId());
        }
        mine = work(me, "내 작품", AgeRating.ALL, true);
    }

    private Series work(User author, String title, AgeRating rating, boolean visible) {
        Series s = seriesRepository.save(Series.create(
                title + (seq++), "", author, rating, SeriesStatus.ONGOING,
                Set.of(DayOfWeek.MONDAY), rating == AgeRating.AGE_19));
        s.changeVisibility(visible);
        episodeRepository.save(Episode.create(s, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
        return s;
    }

    /** 독자 [from, to) 를 그 작품의 독자로 만든다. */
    private void read(Series s, int from, int to) {
        Episode ep = episodeRepository.findBySeriesIdAndEpisodeNo(s.getId(), 1).orElseThrow();
        List<ReadingEvent> evts = new ArrayList<>();
        for (int i = from; i < to; i++) {
            evts.add(ReadingEvent.record(readers.get(i), s.getId(), ep.getId(), 1,
                    EntryPoint.DISCOVER, (short) 50, false, 1000, UUID.randomUUID()));
        }
        readingEventRepository.saveAll(evts);
    }

    private String token(User u) {
        return jwtProvider.createAccessToken(u.getId(), u.getRole());
    }

    private org.springframework.test.web.servlet.ResultActions call(User viewer) throws Exception {
        return mockMvc.perform(get("/api/ontology/works/" + mine.getId() + "/shared-audience")
                .header("Authorization", "Bearer " + token(viewer)));
    }

    @Test
    void 비율의_분모는_항상_내_작품이고_반대방향_수치는_없다() throws Exception {
        read(mine, 0, 12);                     // 내 독자 12명
        Series x = work(other, "X", AgeRating.ALL, true);
        read(x, 0, 6);                         // 그중 6명이 X도 봄
        read(x, 12, 30);                       // X는 훨씬 큰 작품(독자 24명)

        call(me).andExpect(status().isOk())
                .andExpect(jsonPath("$.myAudienceSize").value(12))
                .andExpect(jsonPath("$.links[0].shareOfMyAudience").value(0.5))
                // 상대 규모를 알 수 있는 필드가 응답 어디에도 없다
                .andExpect(jsonPath("$.links[0].overlap").doesNotExist())
                .andExpect(jsonPath("$.links[0].theirAudienceSize").doesNotExist());
    }

    @Test
    void 겹침이_5명_미만이면_목록에서_사라진다() throws Exception {
        read(mine, 0, 12);
        Series few = work(other, "적음", AgeRating.ALL, true);
        read(few, 0, 4);                       // 겹침 4
        Series enough = work(other, "충분", AgeRating.ALL, true);
        read(enough, 0, 5);                    // 겹침 5

        call(me).andExpect(status().isOk())
                .andExpect(jsonPath("$.links.length()").value(1))
                .andExpect(jsonPath("$.links[0].workId").value(enough.getId()));
    }

    @Test
    void 비공개_작품은_제외된다() throws Exception {
        read(mine, 0, 12);
        Series hidden = work(other, "비공개", AgeRating.ALL, false);
        read(hidden, 0, 8);

        call(me).andExpect(status().isOk()).andExpect(jsonPath("$.links.length()").value(0));
    }

    @Test
    void 성인작품은_성인에게만_보인다() throws Exception {
        read(mine, 0, 12);
        Series adult = work(other, "19금", AgeRating.AGE_19, true);
        read(adult, 0, 8);

        call(me).andExpect(status().isOk()).andExpect(jsonPath("$.links.length()").value(1));

        User minorAuthor = userRepository.save(
                User.create("m@t.com", "pw", "미성년작가", Role.CREATOR, MINOR));
        Series minorWork = work(minorAuthor, "미성년 작품", AgeRating.ALL, true);
        read(minorWork, 0, 12);
        mockMvc.perform(get("/api/ontology/works/" + minorWork.getId() + "/shared-audience")
                        .header("Authorization", "Bearer " + token(minorAuthor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links[?(@.workId == " + adult.getId() + ")]").isEmpty());
    }

    /**
     * 카나리아 — 판정 축을 adultOnly로 잘못 잡으면 이 케이스만 통과해버린다.
     * 불변식이 adultOnly ⇒ AGE_19 한 방향뿐이라 adultOnly=false인 AGE_19가 존재한다.
     */
    @Test
    void adultOnly가_false인_19금도_미성년에게_제외된다() throws Exception {
        Series ageOnly = seriesRepository.save(Series.create(
                "연령만19", "", other, AgeRating.AGE_19, SeriesStatus.ONGOING,
                Set.of(DayOfWeek.MONDAY), false));   // adultOnly = false
        episodeRepository.save(Episode.create(ageOnly, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
        read(ageOnly, 0, 8);

        User minorAuthor = userRepository.save(
                User.create("m2@t.com", "pw", "미성년작가2", Role.CREATOR, MINOR));
        Series minorWork = work(minorAuthor, "미성년 작품2", AgeRating.ALL, true);
        read(minorWork, 0, 12);

        mockMvc.perform(get("/api/ontology/works/" + minorWork.getId() + "/shared-audience")
                        .header("Authorization", "Bearer " + token(minorAuthor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links[?(@.workId == " + ageOnly.getId() + ")]").isEmpty());
    }

    @Test
    void 내_독자가_10명_미만이면_계산하지_않는다() throws Exception {
        read(mine, 0, 9);
        Series x = work(other, "X", AgeRating.ALL, true);
        read(x, 0, 9);

        call(me).andExpect(status().isOk())
                .andExpect(jsonPath("$.myAudienceSize").doesNotExist())
                .andExpect(jsonPath("$.links.length()").value(0));
    }

    /**
     * 가시성 필터가 절단보다 먼저인지. 상위 3편이 비공개면 응답은 2건이 아니라
     * 가시적 6·7위를 끌어올려 5건이어야 한다. 절단이 먼저면 결손 개수로
     * "숨겨진 작품이 있다"는 사실이 샌다.
     */
    @Test
    void 가시성_필터가_상위_절단보다_먼저_걸린다() throws Exception {
        read(mine, 0, 20);
        // 겹침 내림차순으로 비공개 3편 + 공개 4편
        int[] overlapDesc = {19, 18, 17, 16, 15, 14, 13};
        for (int i = 0; i < overlapDesc.length; i++) {
            Series s = work(other, "후보" + i, AgeRating.ALL, i >= 3);
            read(s, 0, overlapDesc[i]);
        }

        call(me).andExpect(status().isOk()).andExpect(jsonPath("$.links.length()").value(4));
    }

    @Test
    void 요청한_작품_자신은_목록에_없고_내_다른_작품은_포함된다() throws Exception {
        read(mine, 0, 12);
        Series myOther = work(me, "내 다른 작품", AgeRating.ALL, true);
        read(myOther, 0, 8);

        call(me).andExpect(status().isOk())
                .andExpect(jsonPath("$.links[?(@.workId == " + mine.getId() + ")]").isEmpty())
                .andExpect(jsonPath("$.links[?(@.workId == " + myOther.getId() + ")]").isNotEmpty());
    }

    @Test
    void 타인_작품의_공유독자는_조회할_수_없다() throws Exception {
        read(mine, 0, 12);
        call(other).andExpect(status().isNotFound());
    }
}
