package com.juhkang.apptoon.domain.inquiry;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.juhkang.apptoon.TestcontainersConfiguration;
import com.juhkang.apptoon.domain.auth.JwtProvider;
import com.juhkang.apptoon.domain.user.Role;
import com.juhkang.apptoon.domain.user.User;
import com.juhkang.apptoon.domain.user.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.storage.root=build/test-inquiry-storage")
@AutoConfigureMockMvc
@Transactional
class InquiryFlowTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtProvider jwtProvider;

    private String readerToken;
    private String otherReaderToken;
    private String creatorToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        User reader = userRepository.save(
                User.create("reader@test.com", passwordEncoder.encode("password123"), "독자", Role.READER, ADULT));
        User other = userRepository.save(
                User.create("other@test.com", passwordEncoder.encode("password123"), "다른독자", Role.READER, ADULT));
        User creator = userRepository.save(
                User.create("creator@test.com", passwordEncoder.encode("password123"), "작가", Role.CREATOR, ADULT));
        User admin = userRepository.save(
                User.create("admin@test.com", passwordEncoder.encode("password123"), "관리자", Role.ADMIN, ADULT));
        readerToken = jwtProvider.createAccessToken(reader.getId(), Role.READER);
        otherReaderToken = jwtProvider.createAccessToken(other.getId(), Role.READER);
        creatorToken = jwtProvider.createAccessToken(creator.getId(), Role.CREATOR);
        adminToken = jwtProvider.createAccessToken(admin.getId(), Role.ADMIN);
    }

    private MockMultipartFile pngPart(String name, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return new MockMultipartFile("images", name, "image/png", bos.toByteArray());
    }

    private long submit(String token, String type, String title, String content, MockMultipartFile... images)
            throws Exception {
        var req = multipart("/api/me/inquiries")
                .param("type", type).param("title", title).param("content", content)
                .header("Authorization", "Bearer " + token);
        for (MockMultipartFile f : images) {
            req.file(f);
        }
        String body = mockMvc.perform(req)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void 독자가_이미지와_함께_문의하고_목록과_상세에서_확인한다() throws Exception {
        long id = submit(readerToken, "BUG", "결제 오류 신고", "결제가 두 번 됐어요",
                pngPart("0.png", 1200, 800), pngPart("1.png", 400, 600));

        mockMvc.perform(get("/api/me/inquiries").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("결제 오류 신고"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));

        mockMvc.perform(get("/api/me/inquiries/" + id).header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("BUG"))
                .andExpect(jsonPath("$.content").value("결제가 두 번 됐어요"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.answer").doesNotExist())
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].width").value(800)); // 1200 -> 800 리사이즈
    }

    @Test
    void 이미지_없이도_문의할_수_있다() throws Exception {
        long id = submit(creatorToken, "CREATOR", "정산 문의", "정산일이 궁금합니다");
        mockMvc.perform(get("/api/me/inquiries/" + id).header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(0));
    }

    @Test
    void 타인의_문의는_상세조회할_수_없다_403() throws Exception {
        long id = submit(readerToken, "ACCOUNT", "계정 문의", "비밀번호를 잊었어요");
        mockMvc.perform(get("/api/me/inquiries/" + id).header("Authorization", "Bearer " + otherReaderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void 제목이_비면_400() throws Exception {
        mockMvc.perform(multipart("/api/me/inquiries")
                        .param("type", "ETC").param("title", "  ").param("content", "내용")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 이미지는_최대_5장까지만_허용된다_400() throws Exception {
        mockMvc.perform(multipart("/api/me/inquiries")
                        .file(pngPart("0.png", 100, 100)).file(pngPart("1.png", 100, 100))
                        .file(pngPart("2.png", 100, 100)).file(pngPart("3.png", 100, 100))
                        .file(pngPart("4.png", 100, 100)).file(pngPart("5.png", 100, 100))
                        .param("type", "ETC").param("title", "여섯장").param("content", "too many")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 관리자가_type과_status로_필터링한다() throws Exception {
        submit(readerToken, "BUG", "버그1", "내용");
        submit(readerToken, "PAYMENT", "결제1", "내용");

        mockMvc.perform(get("/api/admin/inquiries").param("type", "BUG")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("BUG"))
                .andExpect(jsonPath("$.content[0].authorNickname").value("독자"));

        mockMvc.perform(get("/api/admin/inquiries").param("status", "PENDING")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void 관리자가_답변하면_ANSWERED가_되고_사용자가_답변을_본다() throws Exception {
        long id = submit(readerToken, "ACCOUNT", "계정 문의", "도와주세요");

        mockMvc.perform(patch("/api/admin/inquiries/" + id + "/answer")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"처리해 드렸습니다\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.answer").value("처리해 드렸습니다"))
                .andExpect(jsonPath("$.authorEmail").value("reader@test.com"));

        mockMvc.perform(get("/api/me/inquiries/" + id).header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.answer").value("처리해 드렸습니다"));
    }

    @Test
    void 관리자가_상태를_CLOSED로_바꾼다() throws Exception {
        long id = submit(readerToken, "ETC", "기타 문의", "내용");
        mockMvc.perform(patch("/api/admin/inquiries/" + id + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void 비관리자는_관리자_문의API에_접근할_수_없다_403() throws Exception {
        mockMvc.perform(get("/api/admin/inquiries").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void 본인은_자신의_문의를_삭제할_수_있고_타인은_못한다() throws Exception {
        long id = submit(readerToken, "ETC", "삭제할 문의", "내용", pngPart("0.png", 100, 100));

        mockMvc.perform(delete("/api/me/inquiries/" + id).header("Authorization", "Bearer " + otherReaderToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/me/inquiries/" + id).header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/inquiries/" + id).header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isNotFound());
    }
}
