package com.juhkang.artiv.domain.series.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesStatus;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;

class SeriesResponseTest {

    @Test
    void 공개여부_visible를_응답에_매핑한다() {
        User author = User.create("a@test.com", "pw", "작가", Role.CREATOR, LocalDate.of(1990, 1, 1));
        Series series = Series.create("제목", "설명", author, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY));

        assertThat(SeriesResponse.of(series).visible()).isTrue();

        series.changeVisibility(false);
        assertThat(SeriesResponse.of(series).visible()).isFalse();
    }
}
