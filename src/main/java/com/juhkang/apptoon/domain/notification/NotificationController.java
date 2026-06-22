package com.juhkang.apptoon.domain.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.apptoon.domain.notification.dto.NotificationResponse;
import com.juhkang.apptoon.domain.notification.dto.UnreadCountResponse;
import com.juhkang.apptoon.domain.notification.dto.UnreadSummaryResponse;
import com.juhkang.apptoon.global.dto.PageResponse;

import lombok.RequiredArgsConstructor;

/** 내 알림함 — 본인만. 종류별 조회·미읽음 집계·읽음 처리(라우팅 정보 반환). 폴링 기반. */
@RestController
@RequestMapping("/api/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public PageResponse<NotificationResponse> mine(@AuthenticationPrincipal Long userId,
                                                   @RequestParam(required = false) NotificationType type,
                                                   @PageableDefault(size = 20) Pageable pageable) {
        return notificationService.getMine(userId, type, pageable);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal Long userId) {
        return new UnreadCountResponse(notificationService.unreadCount(userId));
    }

    @GetMapping("/unread-summary")
    public UnreadSummaryResponse unreadSummary(@AuthenticationPrincipal Long userId) {
        return notificationService.unreadSummary(userId);
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse read(@AuthenticationPrincipal Long userId,
                                     @PathVariable Long notificationId) {
        return notificationService.markRead(userId, notificationId);
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@AuthenticationPrincipal Long userId) {
        notificationService.markAllRead(userId);
    }
}
