package com.juhkang.artiv.domain.chat;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.artiv.domain.chat.dto.ChatUnreadCountResponse;
import com.juhkang.artiv.domain.chat.dto.ConversationCreateRequest;
import com.juhkang.artiv.domain.chat.dto.ConversationResponse;
import com.juhkang.artiv.domain.chat.dto.ConversationSummaryResponse;
import com.juhkang.artiv.domain.chat.dto.MessageCreateRequest;
import com.juhkang.artiv.domain.chat.dto.MessageResponse;
import com.juhkang.artiv.domain.chat.dto.ReadUpToRequest;
import com.juhkang.artiv.global.dto.PageResponse;
import com.juhkang.artiv.global.exception.BusinessException;
import com.juhkang.artiv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 채팅 (CH2) — DIRECT 대화·DM 요청·메시지·읽음·탭 뱃지. GROUP 생성은 CH4에서 확장. */
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/api/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse create(@AuthenticationPrincipal Long userId,
                                       @RequestBody ConversationCreateRequest request) {
        if (request.type() == ConversationType.GROUP) {
            return chatService.createGroup(userId, request.title(), request.memberIds());
        }
        if (request.type() != ConversationType.DIRECT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return chatService.createDirect(userId, request.targetUserId());
    }

    @GetMapping("/api/me/conversations")
    public List<ConversationSummaryResponse> inbox(@AuthenticationPrincipal Long userId) {
        return chatService.inbox(userId);
    }

    @GetMapping("/api/me/conversations/requests")
    public List<ConversationSummaryResponse> requests(@AuthenticationPrincipal Long userId) {
        return chatService.requests(userId);
    }

    @GetMapping("/api/me/conversations/unread-count")
    public ChatUnreadCountResponse unreadCount(@AuthenticationPrincipal Long userId) {
        return chatService.unreadCount(userId);
    }

    @PostMapping("/api/conversations/{conversationId}/accept")
    public ConversationResponse accept(@AuthenticationPrincipal Long userId, @PathVariable Long conversationId) {
        return chatService.accept(userId, conversationId);
    }

    @PostMapping("/api/conversations/{conversationId}/decline")
    public ConversationResponse decline(@AuthenticationPrincipal Long userId, @PathVariable Long conversationId) {
        return chatService.decline(userId, conversationId);
    }

    @GetMapping("/api/conversations/{conversationId}/messages")
    public PageResponse<MessageResponse> messages(@AuthenticationPrincipal Long userId,
                                                  @PathVariable Long conversationId,
                                                  @PageableDefault(size = 30) Pageable pageable) {
        return chatService.getMessages(userId, conversationId, pageable);
    }

    @PostMapping("/api/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@AuthenticationPrincipal Long userId,
                                @PathVariable Long conversationId,
                                @RequestBody MessageCreateRequest request) {
        return chatService.send(userId, conversationId, request.content());
    }

    @PatchMapping("/api/conversations/{conversationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal Long userId,
                     @PathVariable Long conversationId,
                     @RequestBody ReadUpToRequest request) {
        chatService.readUpTo(userId, conversationId, request.lastReadMessageId());
    }
}
