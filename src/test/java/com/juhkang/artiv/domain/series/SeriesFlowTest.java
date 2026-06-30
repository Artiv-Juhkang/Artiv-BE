package com.juhkang.artiv.domain.series;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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

import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.auth.JwtProvider;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeriesFlowTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtProvider jwtProvider;

    private String creatorToken;
    private String readerToken;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(
                User.create("creator@test.com", passwordEncoder.encode("password123"), "작가", Role.CREATOR, ADULT));
        User reader = userRepository.save(
                User.create("reader@test.com", passwordEncoder.encode("password123"), "독자", Role.READER, ADULT));
        creatorToken = jwtProvider.createAccessToken(creator.getId(), Role.CREATOR);
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);
    }

    private void createSeries(String token, String body) throws Exception {
        mockMvc.perform(post("/api/series")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void 작가가_작품을_등록하면_요일필터로_정확히_조회된다() throws Exception {
        createSeries(creatorToken, """
                {"title":"월목웹툰","description":"설명","ageRating":"ALL","status":"ONGOING","publishDays":["MONDAY","THURSDAY"]}""");

        mockMvc.perform(get("/api/series").param("day", "MONDAY")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("월목웹툰"))
                .andExpect(jsonPath("$.content[0].authorNickname").value("작가"));

        mockMvc.perform(get("/api/series").param("day", "TUESDAY")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 독자는_작품을_등록할_수_없다_403() throws Exception {
        mockMvc.perform(post("/api/series")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"x","description":"y","ageRating":"ALL","status":"ONGOING","publishDays":["MONDAY"]}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void 제목_키워드로_작품을_검색한다() throws Exception {
        createSeries(creatorToken, """
                {"title":"로맨스판타지","description":"","ageRating":"ALL","status":"ONGOING","publishDays":["MONDAY"]}""");
        createSeries(creatorToken, """
                {"title":"액션스릴러","description":"","ageRating":"ALL","status":"ONGOING","publishDays":["TUESDAY"]}""");

        mockMvc.perform(get("/api/series").param("keyword", "로맨스")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("로맨스판타지"));

        mockMvc.perform(get("/api/series").param("keyword", "무협")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 작품_상세를_조회한다() throws Exception {
        createSeries(creatorToken, """
                {"title":"상세작품","description":"상세설명","ageRating":"AGE_15","status":"ONGOING","publishDays":["FRIDAY"]}""");

        String listJson = mockMvc.perform(get("/api/series").param("day", "FRIDAY")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer seriesId = com.jayway.jsonpath.JsonPath.read(listJson, "$.content[0].id");

        mockMvc.perform(get("/api/series/" + seriesId)
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("상세작품"))
                .andExpect(jsonPath("$.ageRating").value("AGE_15"))
                .andExpect(jsonPath("$.publishDays[0]").value("FRIDAY"));
    }

    @Test
    void 비웹툰_작품은_연재요일_없이_등록되고_콘텐츠타입이_반영된다() throws Exception {
        createSeries(creatorToken, """
                {"title":"일러스트집","description":"갤러리","ageRating":"ALL","status":"ONGOING","contentType":"ILLUSTRATION"}""");

        String listJson = mockMvc.perform(get("/api/series").param("keyword", "일러스트집")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer seriesId = com.jayway.jsonpath.JsonPath.read(listJson, "$.content[0].id");

        mockMvc.perform(get("/api/series/" + seriesId)
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("ILLUSTRATION"))
                .andExpect(jsonPath("$.publishDays").isEmpty());
    }

    @Test
    void 콘텐츠타입_미지정_작품은_WEBTOON_기본값이다() throws Exception {
        createSeries(creatorToken, """
                {"title":"기본웹툰","description":"","ageRating":"ALL","status":"ONGOING","publishDays":["MONDAY"]}""");

        String listJson = mockMvc.perform(get("/api/series").param("day", "MONDAY")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer seriesId = com.jayway.jsonpath.JsonPath.read(listJson, "$.content[0].id");

        mockMvc.perform(get("/api/series/" + seriesId)
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("WEBTOON"));
    }

    @Test
    void 콘텐츠타입으로_목록을_필터한다() throws Exception {
        createSeries(creatorToken, """
                {"title":"필터웹툰","description":"","ageRating":"ALL","status":"ONGOING","publishDays":["MONDAY"]}""");
        createSeries(creatorToken, """
                {"title":"필터일러","description":"","ageRating":"ALL","status":"ONGOING","contentType":"ILLUSTRATION"}""");

        mockMvc.perform(get("/api/series").param("contentType", "ILLUSTRATION")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("필터일러"))
                .andExpect(jsonPath("$.content[0].contentType").value("ILLUSTRATION"));
    }
}
