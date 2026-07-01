package com.juhkang.artiv.domain.series;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import javax.imageio.ImageIO;

import org.hamcrest.Matchers;
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
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

/** 커버 이미지 업로드(작가 본인만) 저장·조회 검증. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.storage.root=build/test-cover-storage")
@AutoConfigureMockMvc
@Transactional
class SeriesCoverFlowTest {

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
    private String otherToken;
    private Long seriesId;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(
                User.create("c@test.com", passwordEncoder.encode("password123"), "작가", Role.CREATOR, ADULT));
        User other = userRepository.save(
                User.create("o@test.com", passwordEncoder.encode("password123"), "남", Role.CREATOR, ADULT));
        creatorToken = jwtProvider.createAccessToken(creator.getId(), Role.CREATOR);
        otherToken = jwtProvider.createAccessToken(other.getId(), Role.CREATOR);
        seriesId = seriesRepository.save(Series.create(
                "작품", "설명", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY))).getId();
    }

    private MockMultipartFile png() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB), "png", bos);
        return new MockMultipartFile("cover", "cover.png", "image/png", bos.toByteArray());
    }

    @Test
    void 작가가_커버를_올리면_coverUrl이_설정된다() throws Exception {
        mockMvc.perform(multipart("/api/series/" + seriesId + "/cover")
                        .file(png())
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverUrl").value(Matchers.containsString("/cover/")));

        mockMvc.perform(get("/api/series/" + seriesId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverUrl").value(Matchers.containsString("/cover/")));
    }

    @Test
    void 남의_작품에는_커버를_올릴_수_없다_403() throws Exception {
        mockMvc.perform(multipart("/api/series/" + seriesId + "/cover")
                        .file(png())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }
}
