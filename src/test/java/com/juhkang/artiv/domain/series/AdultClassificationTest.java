package com.juhkang.artiv.domain.series;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.auth.JwtProvider;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

/**
 * 성인 전용(adultOnly) 분류 — '성인 전용 웹툰'과 '일반 웹툰에 붙은 19'를 구분.
 * 불변식 adultOnly=true ⇒ ageRating=AGE_19 를 엔티티가 보장한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdultClassificationTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtProvider jwtProvider;

    private String creatorToken;
    private String adminToken;
    private Long age19SeriesId;
    private Long allSeriesId;
    private Long adultSeriesId;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(
                User.create("creator@t.com", passwordEncoder.encode("password123"), "작가", Role.CREATOR, ADULT));
        User admin = userRepository.save(
                User.create("admin@t.com", passwordEncoder.encode("password123"), "관리자", Role.ADMIN, ADULT));
        creatorToken = jwtProvider.createAccessToken(creator.getId(), Role.CREATOR);
        adminToken = jwtProvider.createAccessToken(admin.getId(), Role.ADMIN);

        // 일반물의 19(adultOnly=false), 전체이용가, 성인 전용(adultOnly=true)
        age19SeriesId = seriesRepository.save(Series.create(
                "19작", "", creator, AgeRating.AGE_19, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY))).getId();
        allSeriesId = seriesRepository.save(Series.create(
                "전체작", "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY))).getId();
        adultSeriesId = seriesRepository.save(Series.create(
                "성인전용작", "", creator, AgeRating.AGE_19, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY), true)).getId();
    }

    @Test
    void adultOnly_true_필터는_성인전용만_반환한다() throws Exception {
        mockMvc.perform(get("/api/series").param("adultOnly", "true")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(adultSeriesId))
                .andExpect(jsonPath("$.content[0].adultOnly").value(true));
    }

    @Test
    void adultOnly_false_필터는_성인전용을_제외한다() throws Exception {
        mockMvc.perform(get("/api/series").param("adultOnly", "false")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void ADULT_FIRST_정렬은_성인전용을_먼저_보여준다() throws Exception {
        mockMvc.perform(get("/api/series").param("sort", "ADULT_FIRST")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].adultOnly").value(true));
    }

    @Test
    void adultOnly_true_AGE19_작품을_생성하고_상세에_노출한다() throws Exception {
        String body = mockMvc.perform(post("/api/series")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"성인전용","description":"","ageRating":"AGE_19","status":"ONGOING","publishDays":["MONDAY"],"adultOnly":true}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/series/" + id).header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adultOnly").value(true));
    }

    @Test
    void adultOnly_true_인데_비AGE19면_생성_거부_400() throws Exception {
        mockMvc.perform(post("/api/series")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"모순","description":"","ageRating":"ALL","status":"ONGOING","publishDays":["MONDAY"],"adultOnly":true}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 관리자가_AGE19작품을_성인전용으로_전환한다() throws Exception {
        mockMvc.perform(patch("/api/admin/series/" + age19SeriesId + "/adult-only")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adultOnly\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adultOnly").value(true));
    }

    @Test
    void 비AGE19작품을_성인전용으로_전환하면_400() throws Exception {
        mockMvc.perform(patch("/api/admin/series/" + allSeriesId + "/adult-only")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adultOnly\":true}"))
                .andExpect(status().isBadRequest());
    }
}
