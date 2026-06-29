package com.juhkang.artiv.domain.series;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.auth.JwtProvider;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

/**
 * open-in-view=false 환경에서 상세 응답 직렬화가 LAZY publishDays 로 깨지지 않는지 검증.
 * 일부러 @Transactional 을 붙이지 않는다 — 붙이면 세션이 직렬화 시점까지 유지되어 실서버의 LazyInitializationException 을 못 잡는다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SeriesDetailSerializationTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private JwtProvider jwtProvider;

    private String token;
    private Long seriesId;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(User.create("ser@t.com", "pw", "작가", Role.CREATOR, ADULT));
        token = jwtProvider.createAccessToken(creator.getId(), Role.READER);
        seriesId = seriesRepository.save(Series.create(
                "직렬화작", "설명", creator, AgeRating.ALL, SeriesStatus.ONGOING,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))).getId();
    }

    @AfterEach
    void cleanUp() {
        seriesRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 작품_상세응답의_publishDays가_세션밖_직렬화에서도_정상이다() throws Exception {
        mockMvc.perform(get("/api/series/" + seriesId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishDays").isArray())
                .andExpect(jsonPath("$.publishDays.length()").value(2));
    }
}
