package com.juhkang.artiv.global;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
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
 * API 하드닝(F10~F12) — 클라이언트 실수(잘못된 메서드·누락 파라미터·엉뚱한 sort·잘못된 입력)가
 * 500으로 뭉개지지 않고 정직한 4xx로 응답하는지.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.storage.root=build/test-hardening-storage")
@AutoConfigureMockMvc
@Transactional
class ApiHardeningTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SeriesRepository seriesRepository;
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private JwtProvider jwtProvider;

    private String readerToken;
    private String creatorToken;
    private Long seriesId;

    @BeforeEach
    void setUp() {
        User reader = userRepository.save(User.create("reader@t.com", "pw", "독자", Role.READER, ADULT));
        User creator = userRepository.save(User.create("creator@t.com", "pw", "작가", Role.CREATOR, ADULT));
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);
        creatorToken = jwtProvider.createAccessToken(creator.getId(), Role.CREATOR);

        Series series = seriesRepository.save(Series.create(
                "공개작", "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        seriesId = series.getId();
        episodeRepository.save(Episode.create(series, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
    }

    private MockMultipartFile png() throws IOException {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return new MockMultipartFile("images", "a.png", "image/png", bos.toByteArray());
    }

    @Test
    void 지원하지_않는_HTTP_메서드는_405로_응답한다() throws Exception {
        mockMvc.perform(patch("/api/posts").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void 필수_파라미터가_누락되면_400으로_응답한다() throws Exception {
        // category 파라미터 생략 → MissingServletRequestParameterException
        mockMvc.perform(multipart("/api/posts").header("Authorization", "Bearer " + readerToken)
                        .param("title", "제목").param("content", "내용"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 필수_멀티파트_파트가_누락되면_400으로_응답한다() throws Exception {
        // images 파트 생략 → MissingServletRequestPartException
        mockMvc.perform(multipart("/api/series/" + seriesId + "/episodes")
                        .header("Authorization", "Bearer " + creatorToken)
                        .param("title", "1화"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 엉뚱한_sort_파라미터는_무시되고_200이다() throws Exception {
        mockMvc.perform(get("/api/series/" + seriesId + "/episodes").param("sort", "bogus")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me/notifications").param("sort", "bogus")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me/inquiries").param("sort", "bogus")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk());
    }

    @Test
    void 회차_제목이_비었거나_255자를_넘으면_400이다() throws Exception {
        mockMvc.perform(multipart("/api/series/" + seriesId + "/episodes")
                        .file(png()).header("Authorization", "Bearer " + creatorToken)
                        .param("title", "   "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/series/" + seriesId + "/episodes")
                        .file(png()).header("Authorization", "Bearer " + creatorToken)
                        .param("title", "가".repeat(256)))
                .andExpect(status().isBadRequest());
    }
}
