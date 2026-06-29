package com.juhkang.artiv.domain.series;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.jayway.jsonpath.JsonPath;
import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.auth.JwtProvider;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeriesGenreTagTest {

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
    private String otherCreatorToken;
    private String readerToken;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(
                User.create("creator@test.com", passwordEncoder.encode("password123"), "작가", Role.CREATOR, ADULT));
        User other = userRepository.save(
                User.create("other@test.com", passwordEncoder.encode("password123"), "다른작가", Role.CREATOR, ADULT));
        User reader = userRepository.save(
                User.create("reader@test.com", passwordEncoder.encode("password123"), "독자", Role.READER, ADULT));
        creatorToken = jwtProvider.createAccessToken(creator.getId(), Role.CREATOR);
        otherCreatorToken = jwtProvider.createAccessToken(other.getId(), Role.CREATOR);
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);
    }

    private long createSeries(String json) throws Exception {
        String body = mockMvc.perform(post("/api/series")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void 장르와_태그로_작품을_등록하고_상세에_노출된다() throws Exception {
        long id = createSeries("""
                {"title":"회귀무신","ageRating":"AGE_15","status":"ONGOING","publishDays":["MONDAY"],
                 "genre":"ACTION","tags":["회귀","먼치킨"]}""");

        mockMvc.perform(get("/api/series/" + id).header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genre").value("ACTION"))
                .andExpect(jsonPath("$.tags.length()").value(2));
    }

    @Test
    void 장르_미지정시_ETC_기본값() throws Exception {
        long id = createSeries("""
                {"title":"무장르","ageRating":"ALL","status":"ONGOING","publishDays":["TUESDAY"]}""");

        mockMvc.perform(get("/api/series/" + id).header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genre").value("ETC"))
                .andExpect(jsonPath("$.tags.length()").value(0));
    }

    @Test
    void 장르로_필터링한다() throws Exception {
        createSeries("""
                {"title":"액션물","ageRating":"ALL","status":"ONGOING","publishDays":["MONDAY"],"genre":"ACTION"}""");
        createSeries("""
                {"title":"로맨스물","ageRating":"ALL","status":"ONGOING","publishDays":["MONDAY"],"genre":"ROMANCE"}""");

        mockMvc.perform(get("/api/series").param("genre", "ACTION")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].genre").value("ACTION"));
    }

    @Test
    void 태그로_필터링한다() throws Exception {
        createSeries("""
                {"title":"회귀작","ageRating":"ALL","status":"ONGOING","publishDays":["MONDAY"],
                 "genre":"ACTION","tags":["회귀","먼치킨"]}""");
        createSeries("""
                {"title":"일상작","ageRating":"ALL","status":"ONGOING","publishDays":["MONDAY"],
                 "genre":"DAILY","tags":["일상"]}""");

        mockMvc.perform(get("/api/series").param("tag", "회귀")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("회귀작"));
    }

    @Test
    void 태그는_공백제거되고_중복은_합쳐진다() throws Exception {
        long id = createSeries("""
                {"title":"태그정리","ageRating":"ALL","status":"ONGOING","publishDays":["MONDAY"],
                 "genre":"ETC","tags":["  회귀 ","회귀","  ","먼치킨"]}""");

        mockMvc.perform(get("/api/series/" + id).header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags.length()").value(2));
    }

    @Test
    void 작가가_장르와_태그를_수정한다() throws Exception {
        long id = createSeries("""
                {"title":"수정작","ageRating":"ALL","status":"ONGOING","publishDays":["MONDAY"],
                 "genre":"ACTION","tags":["a"]}""");

        mockMvc.perform(patch("/api/series/" + id + "/genre-tags")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"genre\":\"ROMANCE\",\"tags\":[\"b\",\"c\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genre").value("ROMANCE"))
                .andExpect(jsonPath("$.tags.length()").value(2));

        mockMvc.perform(get("/api/series/" + id).header("Authorization", "Bearer " + readerToken))
                .andExpect(jsonPath("$.genre").value("ROMANCE"));
    }

    @Test
    void 남의_작품_장르태그는_수정할_수_없다_403() throws Exception {
        long id = createSeries("""
                {"title":"내작품","ageRating":"ALL","status":"ONGOING","publishDays":["MONDAY"],"genre":"ACTION"}""");

        mockMvc.perform(patch("/api/series/" + id + "/genre-tags")
                        .header("Authorization", "Bearer " + otherCreatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"genre\":\"ROMANCE\",\"tags\":[]}"))
                .andExpect(status().isForbidden());
    }
}
