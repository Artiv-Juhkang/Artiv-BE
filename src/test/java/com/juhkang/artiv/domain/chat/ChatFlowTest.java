package com.juhkang.artiv.domain.chat;

import static org.hamcrest.Matchers.hasSize;
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

/**
 * 채팅 BE 코어 (CH2) — DIRECT 대화 + DM 요청 수락/거절 + 메시지 + 읽음/미읽음.
 * 설계: docs/improvement/design-2026-07-04.md §2 (폴링 MVP — WebSocket은 전송층 교체로 후속).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatFlowTest {

    private static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    private String aToken;
    private String bToken;
    private String cToken;
    private Long aId;
    private Long bId;
    private Long cId;

    @BeforeEach
    void setUp() {
        User a = userRepository.save(User.create("a@t.com", passwordEncoder.encode("pw"), "에이", Role.READER, ADULT));
        User b = userRepository.save(User.create("b@t.com", passwordEncoder.encode("pw"), "비이", Role.READER, ADULT));
        User c = userRepository.save(User.create("c@t.com", passwordEncoder.encode("pw"), "씨이", Role.READER, ADULT));
        aId = a.getId(); bId = b.getId(); cId = c.getId();
        aToken = jwtProvider.createAccessToken(aId, Role.READER);
        bToken = jwtProvider.createAccessToken(bId, Role.READER);
        cToken = jwtProvider.createAccessToken(cId, Role.READER);
    }

    private long createDirect(String token, Long targetId) throws Exception {
        String body = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DIRECT\",\"targetUserId\":" + targetId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void follow(String token, Long targetId) throws Exception {
        mockMvc.perform(post("/api/users/" + targetId + "/follow").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    @Test
    void 비상호_DM은_PENDING으로_생성되고_수신자에게_알림이_한번만_간다() throws Exception {
        long convId = createDirect(aToken, bId);

        // 재생성 멱등 — 같은 방 반환(direct_key)
        long again = createDirect(aToken, bId);
        org.junit.jupiter.api.Assertions.assertEquals(convId, again);

        // 수신자(B) 요청함에 표시
        mockMvc.perform(get("/api/me/conversations/requests").header("Authorization", "Bearer " + bToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].displayName").value("에이"));

        // DM_REQUEST 알림 1건(dedup — 재생성에도 중복 없음)
        mockMvc.perform(get("/api/me/notifications").header("Authorization", "Bearer " + bToken))
                .andExpect(jsonPath("$.content[?(@.type == 'DM_REQUEST')]", hasSize(1)));

        // 자기 자신 DM → 400
        mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DIRECT\",\"targetUserId\":" + aId + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void PENDING_중에는_요청자만_보낼_수_있다() throws Exception {
        long convId = createDirect(aToken, bId);

        // 요청자(A) 전송 가능 — 인스타 요청함 방식
        mockMvc.perform(post("/api/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"안녕하세요! 작품 잘 보고 있어요.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("안녕하세요! 작품 잘 보고 있어요."));

        // 수신자(B)는 수락 전 전송 불가(암묵적 수락 방지)
        mockMvc.perform(post("/api/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + bToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"...\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 수락하면_양방향_대화가_되고_읽음처리로_미읽음이_0이_된다() throws Exception {
        long convId = createDirect(aToken, bId);
        mockMvc.perform(post("/api/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"첫 메시지\"}"))
                .andExpect(status().isCreated());

        // 수신자가 아닌 사용자는 수락 불가
        mockMvc.perform(post("/api/conversations/" + convId + "/accept").header("Authorization", "Bearer " + aToken))
                .andExpect(status().isForbidden());

        // B 수락 → ACCEPTED
        mockMvc.perform(post("/api/conversations/" + convId + "/accept").header("Authorization", "Bearer " + bToken))
                .andExpect(status().isOk());

        // B 미읽음 1(본인 발신 제외 규칙: A의 메시지 1건)
        mockMvc.perform(get("/api/me/conversations/unread-count").header("Authorization", "Bearer " + bToken))
                .andExpect(jsonPath("$.count").value(1));

        // B 답장 → A 미읽음 1
        String sent = mockMvc.perform(post("/api/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + bToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"반가워요!\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long lastId = ((Number) JsonPath.read(sent, "$.id")).longValue();
        mockMvc.perform(get("/api/me/conversations/unread-count").header("Authorization", "Bearer " + aToken))
                .andExpect(jsonPath("$.count").value(1));

        // 메시지 목록(멤버) — 최신순 Page
        mockMvc.perform(get("/api/conversations/" + convId + "/messages").header("Authorization", "Bearer " + aToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("반가워요!"))
                .andExpect(jsonPath("$.content[1].content").value("첫 메시지"));

        // A 읽음 처리(high-water-mark) → 미읽음 0
        mockMvc.perform(patch("/api/conversations/" + convId + "/read")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\":" + lastId + "}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/conversations/unread-count").header("Authorization", "Bearer " + aToken))
                .andExpect(jsonPath("$.count").value(0));

        // 인박스에 마지막 메시지 미리보기
        mockMvc.perform(get("/api/me/conversations").header("Authorization", "Bearer " + aToken))
                .andExpect(jsonPath("$[0].lastMessage").value("반가워요!"))
                .andExpect(jsonPath("$[0].displayName").value("비이"));
    }

    @Test
    void 거절하면_요청자도_더는_보낼_수_없다() throws Exception {
        long convId = createDirect(aToken, bId);
        mockMvc.perform(post("/api/conversations/" + convId + "/decline").header("Authorization", "Bearer " + bToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"...\"}"))
                .andExpect(status().isForbidden());
        // 거절된 방은 수신자 요청함·발신자 인박스에서 제외(조용한 거절)
        mockMvc.perform(get("/api/me/conversations/requests").header("Authorization", "Bearer " + bToken))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/me/conversations").header("Authorization", "Bearer " + aToken))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 상호팔로우_친구끼리는_요청_없이_즉시_대화가_열린다() throws Exception {
        follow(aToken, cId);
        follow(cToken, aId); // 상호 = 친구

        long convId = createDirect(aToken, cId);
        mockMvc.perform(get("/api/me/conversations").header("Authorization", "Bearer " + cToken))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));
        // 요청 알림 없음
        mockMvc.perform(get("/api/me/notifications").header("Authorization", "Bearer " + cToken))
                .andExpect(jsonPath("$.content[?(@.type == 'DM_REQUEST')]", hasSize(0)));
        // 수신자도 즉시 전송 가능
        mockMvc.perform(post("/api/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + cToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"바로 대화!\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void 읽음처리는_다른_대화의_메시지_id를_거부한다() throws Exception {
        long convAB = createDirect(aToken, bId);
        follow(aToken, cId);
        follow(cToken, aId); // 상호=친구 → 즉시 ACCEPTED
        long convAC = createDirect(aToken, cId);

        String sent = mockMvc.perform(post("/api/conversations/" + convAC + "/messages")
                        .header("Authorization", "Bearer " + cToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"다른 대화의 메시지\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long foreignMessageId = ((Number) JsonPath.read(sent, "$.id")).longValue();

        // convAB의 멤버(A)가 convAC 소속 메시지 id로 읽음처리 시도 → 거부
        mockMvc.perform(patch("/api/conversations/" + convAB + "/read")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\":" + foreignMessageId + "}"))
                .andExpect(status().isBadRequest());

        // 존재하지 않는 메시지 id도 거부
        mockMvc.perform(patch("/api/conversations/" + convAB + "/read")
                        .header("Authorization", "Bearer " + aToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\":999999}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 비멤버는_대화를_볼_수_없다() throws Exception {
        long convId = createDirect(aToken, bId);
        mockMvc.perform(get("/api/conversations/" + convId + "/messages").header("Authorization", "Bearer " + cToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + cToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"끼어들기\"}"))
                .andExpect(status().isForbidden());
    }
}
