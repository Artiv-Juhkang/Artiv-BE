package com.juhkang.artiv.domain.ontology;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
import com.juhkang.artiv.domain.user.Role;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;

/** 온톨로지 스키마 노출 — ContentType 레지스트리와 같은 "타입=데이터" 패턴. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OntologySchemaTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtProvider jwtProvider;

    private String creatorToken;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(
                User.create("c@t.com", "pw", "작가", Role.CREATOR, LocalDate.of(1990, 1, 1)));
        creatorToken = jwtProvider.createAccessToken(creator.getId(), Role.CREATOR);
    }

    @Test
    void 스키마는_객체8_링크8_액션3을_노출한다() throws Exception {
        mockMvc.perform(get("/api/ontology/schema")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects.length()").value(8))
                .andExpect(jsonPath("$.links.length()").value(8))
                .andExpect(jsonPath("$.actions.length()").value(3));
    }

    @Test
    void 개인_독자는_객체로_노출되지_않는다() throws Exception {
        mockMvc.perform(get("/api/ontology/schema")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects[?(@.key == 'READER')]").isEmpty());
    }

    @Test
    void 파생객체는_derived_true로_표시된다() throws Exception {
        mockMvc.perform(get("/api/ontology/schema")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects[?(@.key == 'AUDIENCE_SEGMENT')].derived").value(true))
                .andExpect(jsonPath("$.objects[?(@.key == 'WORK')].derived").value(false));
    }

    @Test
    void 액션은_백킹_엔드포인트를_함께_노출한다() throws Exception {
        mockMvc.perform(get("/api/ontology/schema")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions[?(@.key == 'RETAG_WORK')].endpoint")
                        .value("/api/series/{id}/genre-tags"))
                .andExpect(jsonPath("$.actions[?(@.key == 'RETAG_WORK')].method").value("PATCH"));
    }
}
