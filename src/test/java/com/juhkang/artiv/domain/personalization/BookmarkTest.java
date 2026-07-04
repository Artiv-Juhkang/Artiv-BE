package com.juhkang.artiv.domain.personalization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
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

/** 회차 북마크 — 멱등 토글 + 내 북마크 목록(작품·회차 정보). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookmarkTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private EpisodeRepository episodeRepository;
    @Autowired
    private JwtProvider jwtProvider;

    private String readerToken;
    private String bookmarkUrl;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(User.create("creator@t.com", "pw", "작가", Role.CREATOR, ADULT));
        User reader = userRepository.save(User.create("reader@t.com", "pw", "독자", Role.READER, ADULT));
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);

        Series series = seriesRepository.save(Series.create(
                "북마크작품", "", creator, AgeRating.ALL, SeriesStatus.ONGOING, Set.of(DayOfWeek.MONDAY)));
        episodeRepository.save(Episode.create(series, 1, "1화", EpisodeStatus.PUBLISHED, Instant.now()));
        bookmarkUrl = "/api/series/" + series.getId() + "/episodes/1/bookmark";
    }

    private void assertBookmarkCount(int count) throws Exception {
        mockMvc.perform(get("/api/me/bookmarks").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(count));
    }

    @Test
    void 북마크는_멱등_토글이고_내_목록에_작품과_회차가_보인다() throws Exception {
        assertBookmarkCount(0);

        mockMvc.perform(post(bookmarkUrl).header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isCreated());
        // 중복 북마크는 멱등
        mockMvc.perform(post(bookmarkUrl).header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/me/bookmarks").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].seriesTitle").value("북마크작품"))
                .andExpect(jsonPath("$.content[0].episodeNo").value(1));

        mockMvc.perform(delete(bookmarkUrl).header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isNoContent());
        assertBookmarkCount(0);
    }
}
