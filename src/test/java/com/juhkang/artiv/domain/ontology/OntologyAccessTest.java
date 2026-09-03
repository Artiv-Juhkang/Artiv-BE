package com.juhkang.artiv.domain.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesRepository;
import com.juhkang.artiv.domain.series.SeriesStatus;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

/** 온톨로지 접근 통제 — 타인 작품은 존재 은닉(404), 소수 세그먼트는 비공개. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class OntologyAccessTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private OntologyAccessChecker checker;
    @Autowired private UserRepository userRepository;
    @Autowired private SeriesRepository seriesRepository;

    private Long ownerId;
    private Long strangerId;
    private Long seriesId;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(User.create("o@t.com", "pw", "주인", Role.CREATOR, ADULT));
        User stranger = userRepository.save(User.create("s@t.com", "pw", "타인", Role.CREATOR, ADULT));
        ownerId = owner.getId();
        strangerId = stranger.getId();
        seriesId = seriesRepository.save(Series.create(
                "작품", "", owner, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY))).getId();
    }

    @Test
    void 작가_본인은_자기_작품에_접근한다() {
        assertThat(checker.requireOwnedWork(seriesId, ownerId).getId()).isEqualTo(seriesId);
    }

    @Test
    void 타인_작품은_403이_아니라_404다() {
        assertThatThrownBy(() -> checker.requireOwnedWork(seriesId, strangerId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENTITY_NOT_FOUND);
    }

    @Test
    void 없는_작품도_404다() {
        assertThatThrownBy(() -> checker.requireOwnedWork(999_999L, ownerId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENTITY_NOT_FOUND);
    }

    @Test
    void 세그먼트가_5명_미만이면_공개하지_않는다() {
        assertThat(checker.isDisclosable(4)).isFalse();
        assertThat(checker.isDisclosable(5)).isTrue();
        assertThat(checker.isDisclosable(0)).isFalse();
    }
}
