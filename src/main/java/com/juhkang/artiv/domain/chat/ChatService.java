package com.juhkang.artiv.domain.chat;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juhkang.artiv.domain.chat.dto.ChatUnreadCountResponse;
import com.juhkang.artiv.domain.chat.dto.ConversationResponse;
import com.juhkang.artiv.domain.chat.dto.ConversationSummaryResponse;
import com.juhkang.artiv.domain.chat.dto.MessageResponse;
import com.juhkang.artiv.domain.follow.FollowRepository;
import com.juhkang.artiv.domain.notification.NotificationService;
import com.juhkang.artiv.domain.notification.NotificationTargetType;
import com.juhkang.artiv.domain.notification.NotificationType;
import com.juhkang.artiv.domain.user.User;
import com.juhkang.artiv.domain.user.UserRepository;
import com.juhkang.artiv.global.dto.PageResponse;
import com.juhkang.artiv.global.dto.Pageables;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;
import com.juhkang.artiv.global.storage.ImageStorageService;

import lombok.RequiredArgsConstructor;

/**
 * 채팅 코어 (CH2) — DIRECT 대화 + DM 요청 수락/거절 + 메시지 + high-water-mark 읽음.
 * 폴링 MVP: 전송층을 WebSocket으로 바꿔도 이 서비스/모델은 불변(기존 결정).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private static final int MAX_CONTENT = 2000;

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ImageStorageService imageStorageService;

    /**
     * DIRECT 대화 생성(멱등) — direct_key('minId:maxId') 유니크가 같은 쌍의 중복·동시 생성을 차단.
     * 친구(상호 팔로우)면 즉시 ACCEPTED, 아니면 PENDING + 수신자에게 DM_REQUEST 알림(dedup).
     */
    @Transactional
    public ConversationResponse createDirect(Long userId, Long targetUserId) {
        if (targetUserId == null || targetUserId.equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); // 자기 자신 DM 금지
        }
        if (!userRepository.existsById(targetUserId)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        String key = directKey(userId, targetUserId);
        return conversationRepository.findByDirectKey(key)
                .map(ConversationResponse::of)
                .orElseGet(() -> createDirectInternal(userId, targetUserId, key));
    }

    private ConversationResponse createDirectInternal(Long userId, Long targetUserId, String key) {
        boolean mutual = followRepository.existsByFollowerIdAndFollowingId(userId, targetUserId)
                && followRepository.existsByFollowerIdAndFollowingId(targetUserId, userId);
        Conversation conversation;
        try {
            conversation = conversationRepository.save(Conversation.direct(userId, key, mutual));
            memberRepository.save(ConversationMember.create(conversation.getId(), userId));
            memberRepository.save(ConversationMember.create(conversation.getId(), targetUserId));
        } catch (DataIntegrityViolationException race) {
            // 동시 생성 경쟁 — 유니크 충돌이면 이미 만들어진 방을 반환(멱등).
            return conversationRepository.findByDirectKey(key)
                    .map(ConversationResponse::of)
                    .orElseThrow(() -> race);
        }
        if (conversation.getStatus() == ConversationStatus.PENDING) {
            String nickname = userRepository.findById(userId).map(User::getNickname).orElse("누군가");
            notificationService.fanOut(List.of(targetUserId), NotificationType.DM_REQUEST,
                    NotificationTargetType.CONVERSATION, conversation.getId(), userId,
                    "새 메시지 요청", nickname + "님이 대화를 요청했어요.",
                    rid -> "DM_REQUEST:" + conversation.getId());
        }
        return ConversationResponse.of(conversation);
    }

    /** 요청 수락 — DIRECT PENDING의 수신자만. */
    @Transactional
    public ConversationResponse accept(Long userId, Long conversationId) {
        Conversation conversation = loadPendingForReceiver(userId, conversationId);
        conversation.accept();
        return ConversationResponse.of(conversation);
    }

    /** 요청 거절 — 영구(재요청은 direct_key 유니크가 자연 차단, 조용한 거절 D-확4). */
    @Transactional
    public ConversationResponse decline(Long userId, Long conversationId) {
        Conversation conversation = loadPendingForReceiver(userId, conversationId);
        conversation.decline();
        return ConversationResponse.of(conversation);
    }

    /** 메시지 전송 — ACCEPTED 멤버, PENDING이면 요청자만(수신자의 응답이 암묵적 수락이 되는 것 방지). */
    @Transactional
    public MessageResponse send(Long userId, Long conversationId, String content) {
        if (content == null || content.isBlank() || content.strip().length() > MAX_CONTENT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Conversation conversation = loadAsMember(userId, conversationId);
        if (conversation.getStatus() == ConversationStatus.DECLINED
                || (conversation.getStatus() == ConversationStatus.PENDING && !conversation.isRequester(userId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Message saved = messageRepository.save(Message.create(conversationId, userId, content.strip()));
        String nickname = userRepository.findById(userId).map(User::getNickname).orElse("(탈퇴)");
        return MessageResponse.of(saved, nickname);
    }

    /** 대화 메시지(최신순 Page) — 멤버만. */
    public PageResponse<MessageResponse> getMessages(Long userId, Long conversationId, Pageable pageable) {
        loadAsMember(userId, conversationId);
        Page<Message> page = messageRepository.findPageByConversationId(conversationId, Pageables.pageOnly(pageable));
        Map<Long, String> names = nicknames(page.getContent().stream().map(Message::getSenderId).toList());
        return PageResponse.from(page.map(m -> MessageResponse.of(m, names.getOrDefault(m.getSenderId(), "(탈퇴)"))));
    }

    /** 읽음 포인터 전진 — 멤버만, 후퇴 없음. */
    @Transactional
    public void readUpTo(Long userId, Long conversationId, Long lastReadMessageId) {
        ConversationMember member = memberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        member.readUpTo(lastReadMessageId);
    }

    /** 인박스 — ACCEPTED 전부 + 내가 보낸 PENDING(응답 대기 중인 요청). DECLINED 제외. */
    public List<ConversationSummaryResponse> inbox(Long userId) {
        return summaries(userId, c ->
                c.getStatus() == ConversationStatus.ACCEPTED
                        || (c.getStatus() == ConversationStatus.PENDING && c.isRequester(userId)));
    }

    /** 요청함 — 내가 받은 PENDING. */
    public List<ConversationSummaryResponse> requests(Long userId) {
        return summaries(userId, c ->
                c.getStatus() == ConversationStatus.PENDING && !c.isRequester(userId));
    }

    /** 채팅 탭 뱃지 — 전체 미읽음(본인 발신 제외, DECLINED 제외). 알림 카운트와 분리(SSOT). */
    public ChatUnreadCountResponse unreadCount(Long userId) {
        return new ChatUnreadCountResponse(messageRepository.countUnreadTotal(userId));
    }

    /* ------------------------------------------------------------------ */

    private List<ConversationSummaryResponse> summaries(Long userId, java.util.function.Predicate<Conversation> filter) {
        List<ConversationMember> myMemberships = memberRepository.findByUserId(userId);
        if (myMemberships.isEmpty()) {
            return List.of();
        }
        List<Long> conversationIds = myMemberships.stream().map(ConversationMember::getConversationId).toList();
        List<Conversation> conversations = conversationRepository.findAllById(conversationIds).stream()
                .filter(filter)
                .toList();
        if (conversations.isEmpty()) {
            return List.of();
        }
        List<Long> ids = conversations.stream().map(Conversation::getId).toList();

        // 배치 3쿼리(멤버·마지막 메시지·미읽음) + 닉네임 1쿼리 — 인박스 N+1 없음.
        Map<Long, Long> partnerByConv = memberRepository.findByConversationIdIn(ids).stream()
                .filter(m -> !m.getUserId().equals(userId))
                .collect(Collectors.toMap(ConversationMember::getConversationId, ConversationMember::getUserId, (a, b) -> a));
        Map<Long, Message> latestByConv = messageRepository.findLatestByConversationIds(ids).stream()
                .collect(Collectors.toMap(Message::getConversationId, m -> m));
        Map<Long, Long> unreadByConv = new HashMap<>();
        for (Object[] row : messageRepository.countUnreadByConversationIds(userId, ids)) {
            unreadByConv.put((Long) row[0], (Long) row[1]);
        }
        Map<Long, User> partners = userRepository.findAllById(
                        partnerByConv.values().stream().distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return conversations.stream()
                .map(c -> {
                    Long partnerId = partnerByConv.get(c.getId());
                    User partner = partnerId == null ? null : partners.get(partnerId);
                    String displayName = c.getType() == ConversationType.GROUP
                            ? c.getTitle()
                            : partner != null ? partner.getNickname() : "(탈퇴)";
                    String avatarUrl = partner != null && partner.getAvatarKey() != null
                            ? imageStorageService.urlFor(partner.getAvatarKey())
                            : null;
                    Message latest = latestByConv.get(c.getId());
                    return new ConversationSummaryResponse(
                            c.getId(), c.getType(), c.getStatus(), displayName, partnerId, avatarUrl,
                            latest != null ? latest.getContent() : null,
                            latest != null ? latest.getCreatedAt() : null,
                            unreadByConv.getOrDefault(c.getId(), 0L));
                })
                // 최근 대화가 위로 — 메시지 없는 방은 생성 시각 기준.
                .sorted(Comparator.comparing((ConversationSummaryResponse s) ->
                                s.lastMessageAt() == null ? java.time.Instant.MIN : s.lastMessageAt())
                        .reversed())
                .toList();
    }

    private Conversation loadPendingForReceiver(Long userId, Long conversationId) {
        Conversation conversation = loadAsMember(userId, conversationId);
        if (conversation.getStatus() != ConversationStatus.PENDING || conversation.isRequester(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN); // 수신자만, PENDING에서만
        }
        return conversation;
    }

    private Conversation loadAsMember(Long userId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        if (!memberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return conversation;
    }

    private Map<Long, String> nicknames(Collection<Long> userIds) {
        return userRepository.findAllById(userIds.stream().filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));
    }

    private String directKey(Long a, Long b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }
}
