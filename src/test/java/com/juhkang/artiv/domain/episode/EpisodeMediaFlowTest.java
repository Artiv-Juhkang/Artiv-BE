package com.juhkang.artiv.domain.episode;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.TestcontainersConfiguration;
import com.juhkang.artiv.domain.auth.JwtProvider;
import com.juhkang.artiv.domain.series.AgeRating;
import com.juhkang.artiv.domain.series.ContentType;
import com.juhkang.artiv.domain.series.Series;
import com.juhkang.artiv.domain.series.SeriesRepository;
import com.juhkang.artiv.domain.series.SeriesStatus;
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

/**
 * 다매체 회차 업로드(소설=TEXT, 음악=AUDIO) 저장·조회 검증.
 * 작품 타입이 자산 종류를 결정하고, 비이미지 자산은 원본 그대로 저장돼 mediaKind/mimeType로 노출된다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.storage.root=build/test-media-storage")
@AutoConfigureMockMvc
@Transactional
class EpisodeMediaFlowTest {

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
    private String readerToken;
    private User creator;

    @BeforeEach
    void setUp() {
        creator = userRepository.save(
                User.create("creator@test.com", passwordEncoder.encode("password123"), "작가", Role.CREATOR, ADULT));
        User reader = userRepository.save(
                User.create("reader@test.com", passwordEncoder.encode("password123"), "독자", Role.READER, ADULT));
        creatorToken = jwtProvider.createAccessToken(creator.getId(), Role.CREATOR);
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);
    }

    private Long newSeries(ContentType type) {
        Series series = seriesRepository.save(Series.create(
                "작품-" + type, "설명", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(), false, type));
        return series.getId();
    }

    @Test
    void 소설_회차는_텍스트_자산으로_저장되고_TEXT로_조회된다() throws Exception {
        Long seriesId = newSeries(ContentType.NOVEL);
        MockMultipartFile text = new MockMultipartFile(
                "images", "1.txt", "text/plain", "옛날 옛적에 한 작가가 있었다.".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/series/" + seriesId + "/episodes")
                        .file(text)
                        .param("title", "1화")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.episodeNo").value(1));

        mockMvc.perform(get("/api/series/" + seriesId + "/episodes/1")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(1))
                .andExpect(jsonPath("$.images[0].mediaKind").value("TEXT"))
                .andExpect(jsonPath("$.images[0].mimeType").value("text/plain"))
                .andExpect(jsonPath("$.images[0].url").value(org.hamcrest.Matchers.endsWith(".txt")))
                .andExpect(jsonPath("$.images[0].width").doesNotExist());
    }

    @Test
    void 음악_회차는_오디오_자산으로_저장되고_AUDIO로_조회된다() throws Exception {
        Long seriesId = newSeries(ContentType.AUDIO);
        MockMultipartFile audio = new MockMultipartFile(
                "images", "1.mp3", "audio/mpeg", new byte[]{0x49, 0x44, 0x33, 0x04, 0, 0, 0, 0});

        mockMvc.perform(multipart("/api/series/" + seriesId + "/episodes")
                        .file(audio)
                        .param("title", "1화")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/series/" + seriesId + "/episodes/1")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].mediaKind").value("AUDIO"))
                .andExpect(jsonPath("$.images[0].mimeType").value("audio/mpeg"))
                .andExpect(jsonPath("$.images[0].url").value(org.hamcrest.Matchers.endsWith(".mp3")));
    }

    @Test
    void 소설_작품에_이미지를_올리면_거부된다_400() throws Exception {
        Long seriesId = newSeries(ContentType.NOVEL);
        MockMultipartFile png = new MockMultipartFile(
                "images", "0.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/series/" + seriesId + "/episodes")
                        .file(png)
                        .param("title", "1화")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isBadRequest());
    }
}
