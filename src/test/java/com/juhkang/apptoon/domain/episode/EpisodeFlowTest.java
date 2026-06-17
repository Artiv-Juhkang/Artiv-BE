package com.juhkang.apptoon.domain.episode;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

import javax.imageio.ImageIO;

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

import com.juhkang.apptoon.TestcontainersConfiguration;
import com.juhkang.apptoon.domain.auth.JwtProvider;
import com.juhkang.apptoon.domain.episode.EpisodeService;
import com.juhkang.apptoon.domain.series.AgeRating;
import com.juhkang.apptoon.domain.series.Series;
import com.juhkang.apptoon.domain.series.SeriesRepository;
import com.juhkang.apptoon.domain.series.SeriesStatus;
import com.juhkang.apptoon.domain.user.Role;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.storage.root=build/test-episode-storage")
@AutoConfigureMockMvc
@Transactional
class EpisodeFlowTest {

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
    @Autowired
    private EpisodeService episodeService;

    private String creatorToken;
    private String otherCreatorToken;
    private String readerToken;
    private Long seriesId;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(
                User.create("creator@test.com", passwordEncoder.encode("password123"), "작가", Role.CREATOR, ADULT));
        User otherCreator = userRepository.save(
                User.create("other@test.com", passwordEncoder.encode("password123"), "다른작가", Role.CREATOR, ADULT));
        User reader = userRepository.save(
                User.create("reader@test.com", passwordEncoder.encode("password123"), "독자", Role.READER, ADULT));
        creatorToken = jwtProvider.createAccessToken(creator.getId(), Role.CREATOR);
        otherCreatorToken = jwtProvider.createAccessToken(otherCreator.getId(), Role.CREATOR);
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);

        Series series = seriesRepository.save(Series.create(
                "테스트작품", "설명", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        seriesId = series.getId();
    }

    private MockMultipartFile pngPart(String name, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return new MockMultipartFile("images", name, "image/png", bos.toByteArray());
    }

    @Test
    void 작가가_회차를_업로드하면_순서대로_저장되고_와이드_이미지는_리사이즈된다() throws Exception {
        mockMvc.perform(multipart("/api/series/" + seriesId + "/episodes")
                        .file(pngPart("0.png", 1200, 2000))
                        .file(pngPart("1.png", 400, 800))
                        .file(pngPart("2.png", 600, 900))
                        .param("title", "1화")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.episodeNo").value(1));

        mockMvc.perform(get("/api/series/" + seriesId + "/episodes/1")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("1화"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.images.length()").value(3))
                .andExpect(jsonPath("$.images[0].sortOrder").value(0))
                .andExpect(jsonPath("$.images[1].sortOrder").value(1))
                .andExpect(jsonPath("$.images[2].sortOrder").value(2))
                .andExpect(jsonPath("$.images[0].width").value(800))   // 1200 -> 800 리사이즈
                .andExpect(jsonPath("$.images[1].width").value(400));  // 800 이하 → 원본 유지
    }

    @Test
    void 독자는_회차를_업로드할_수_없다_403() throws Exception {
        mockMvc.perform(multipart("/api/series/" + seriesId + "/episodes")
                        .file(pngPart("0.png", 400, 800))
                        .param("title", "1화")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void 다른_작가는_남의_작품에_회차를_업로드할_수_없다_403() throws Exception {
        mockMvc.perform(multipart("/api/series/" + seriesId + "/episodes")
                        .file(pngPart("0.png", 400, 800))
                        .param("title", "1화")
                        .header("Authorization", "Bearer " + otherCreatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void 예약_업로드는_SCHEDULED_상태로_저장되고_발행시각이_지나면_PUBLISHED로_전환된다() throws Exception {
        Instant publishAt = Instant.now().plusSeconds(3600);

        mockMvc.perform(multipart("/api/series/" + seriesId + "/episodes")
                        .file(pngPart("0.png", 400, 800))
                        .param("title", "예약화")
                        .param("publishAt", publishAt.toString())
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.episodeNo").value(1));

        // 예약 상태: 상세는 SCHEDULED, 발행 목록에는 아직 안 보임
        mockMvc.perform(get("/api/series/" + seriesId + "/episodes/1")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
        mockMvc.perform(get("/api/series/" + seriesId + "/episodes")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        // 발행 시각이 지난 것처럼 스케줄러를 직접 호출 → PUBLISHED 전환
        episodeService.publishDueEpisodes(publishAt.plusSeconds(1));

        mockMvc.perform(get("/api/series/" + seriesId + "/episodes")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].episodeNo").value(1));
    }
}
