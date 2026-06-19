package com.juhkang.apptoon.domain.series;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
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

import com.juhkang.apptoon.TestcontainersConfiguration;
import com.juhkang.apptoon.domain.auth.JwtProvider;
import com.juhkang.apptoon.domain.user.Role;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;

/** 작가가 자기 작품을 (비공개 포함) 공개여부와 함께 조회하는 GET /api/series/mine 검증. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MySeriesTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private JwtProvider jwtProvider;

    private String authorToken;
    private String readerToken;

    @BeforeEach
    void setUp() {
        User author = userRepository.save(User.create("author@t.com", "pw", "작가", Role.CREATOR, ADULT));
        User reader = userRepository.save(User.create("reader@t.com", "pw", "독자", Role.READER, ADULT));
        authorToken = jwtProvider.createAccessToken(author.getId(), Role.CREATOR);
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);

        seriesRepository.save(Series.create(
                "공개작", "", author, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        Series hidden = Series.create(
                "비공개작", "", author, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY));
        hidden.changeVisibility(false);
        seriesRepository.save(hidden);
    }

    @Test
    void 작가는_자기작품을_비공개포함_공개여부와_함께_조회한다() throws Exception {
        mockMvc.perform(get("/api/series/mine").header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.title=='비공개작')].visible").value(false))
                .andExpect(jsonPath("$[?(@.title=='공개작')].visible").value(true));
    }

    @Test
    void 다른_사용자는_자기작품만_본다_빈목록() throws Exception {
        mockMvc.perform(get("/api/series/mine").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
