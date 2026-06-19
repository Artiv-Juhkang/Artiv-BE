package com.juhkang.apptoon.domain.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.juhkang.apptoon.TestcontainersConfiguration;
import com.juhkang.apptoon.domain.auth.JwtProvider;
import com.juhkang.apptoon.domain.series.AgeRating;
import com.juhkang.apptoon.domain.series.Series;
import com.juhkang.apptoon.domain.series.SeriesRepository;
import com.juhkang.apptoon.domain.series.SeriesStatus;
import com.juhkang.apptoon.domain.user.Role;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminFlowTest {

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

    private String adminToken;
    private String creatorToken;
    private String readerToken;
    private Long readerId;
    private Long seriesId;

    @BeforeEach
    void setUp() {
        User admin = userRepository.save(
                User.create("admin@test.com", passwordEncoder.encode("password123"), "관리자", Role.ADMIN, ADULT));
        User creator = userRepository.save(
                User.create("creator@test.com", passwordEncoder.encode("password123"), "작가", Role.CREATOR, ADULT));
        User reader = userRepository.save(
                User.create("reader@test.com", passwordEncoder.encode("password123"), "독자", Role.READER, ADULT));
        adminToken = jwtProvider.createAccessToken(admin.getId(), Role.ADMIN);
        creatorToken = jwtProvider.createAccessToken(creator.getId(), Role.CREATOR);
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);
        readerId = reader.getId();

        Series series = seriesRepository.save(Series.create(
                "작품", "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        seriesId = series.getId();
    }

    @Test
    void 비관리자는_관리자_API에_접근할_수_없다_403() throws Exception {
        mockMvc.perform(patch("/api/admin/series/" + seriesId + "/age-rating")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ageRating\":\"AGE_19\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/users/" + readerId + "/role")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CREATOR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 관리자는_작품_연령등급을_변경한다() throws Exception {
        mockMvc.perform(patch("/api/admin/series/" + seriesId + "/age-rating")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ageRating\":\"AGE_19\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ageRating").value("AGE_19"));
    }

    @Test
    void 관리자는_사용자에게_작가_권한을_부여한다() throws Exception {
        mockMvc.perform(patch("/api/admin/users/" + readerId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CREATOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CREATOR"));
    }

    @Test
    void 관리자가_작품을_비공개하면_공개목록에서_빠진다() throws Exception {
        mockMvc.perform(get("/api/series").param("day", "MONDAY")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(patch("/api/admin/series/" + seriesId + "/visibility")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(false));

        mockMvc.perform(get("/api/series").param("day", "MONDAY")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 관리자는_사용자를_닉네임으로_검색한다() throws Exception {
        mockMvc.perform(get("/api/admin/users").param("keyword", "독자")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nickname").value("독자"));
    }

    @Test
    void 관리자는_역할로_사용자를_필터한다() throws Exception {
        mockMvc.perform(get("/api/admin/users").param("role", "ADMIN")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].role").value("ADMIN"));
    }

    @Test
    void 관리자는_비공개_작품도_목록에서_조회한다() throws Exception {
        mockMvc.perform(patch("/api/admin/series/" + seriesId + "/visibility")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":false}"))
                .andExpect(status().isOk());
        // 공개 목록(GET /api/series)에선 빠지지만, 관리자 목록엔 보인다
        mockMvc.perform(get("/api/admin/series").param("visible", "false")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].visible").value(false));
    }

    @Test
    void 비관리자는_관리자_목록API에_접근할_수_없다_403() throws Exception {
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isForbidden());
    }
}
