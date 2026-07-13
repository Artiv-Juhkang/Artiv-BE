package com.juhkang.artiv.domain.community;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

/** 게시글 카테고리 등록제(C7, 확정 D1=B) — enum→FK, 사용자 신규 등록. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.storage.root=build/test-post-category-storage")
@AutoConfigureMockMvc
@Transactional
class PostCategoryFlowTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    private String readerToken;

    @BeforeEach
    void setUp() {
        User reader = userRepository.save(User.create("reader@t.com", passwordEncoder.encode("pw"), "독자", Role.READER, ADULT));
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);
    }

    @Test
    void 시드된_기본_카테고리_4종이_조회된다() throws Exception {
        mockMvc.perform(get("/api/post-categories").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].name", hasItem("추천")))
                .andExpect(jsonPath("$[*].name", hasItem("자유")))
                .andExpect(jsonPath("$[*].name", hasItem("팬아트")))
                .andExpect(jsonPath("$[*].name", hasItem("질문")));
    }

    @Test
    void 새_카테고리를_등록하면_목록에_보이고_바로_글을_쓸_수_있다() throws Exception {
        mockMvc.perform(post("/api/post-categories").header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"창작후기\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("창작후기"));

        mockMvc.perform(get("/api/post-categories").header("Authorization", "Bearer " + readerToken))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[*].name", hasItem("창작후기")));

        mockMvc.perform(multipart("/api/posts").header("Authorization", "Bearer " + readerToken)
                        .param("category", "창작후기").param("title", "새 카테고리 글").param("content", "본문"))
                .andExpect(status().isCreated());
    }

    @Test
    void 같은_이름을_다시_등록하면_409() throws Exception {
        mockMvc.perform(post("/api/post-categories").header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"자유\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void 빈_이름이나_20자_초과는_400() throws Exception {
        mockMvc.perform(post("/api/post-categories").header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/post-categories").header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"123456789012345678901\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 등록되지_않은_카테고리로는_글을_쓸_수_없다_400() throws Exception {
        mockMvc.perform(multipart("/api/posts").header("Authorization", "Bearer " + readerToken)
                        .param("category", "없는카테고리").param("title", "제목").param("content", "본문"))
                .andExpect(status().isBadRequest());
    }
}
