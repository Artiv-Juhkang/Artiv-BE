package com.juhkang.apptoon.domain.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.apptoon.domain.user.dto.CreatorRequestResponse;
import com.juhkang.apptoon.domain.user.dto.CreatorRequestSubmitRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 본인 작가 전환 신청·상태(독자가 신청). */
@RestController
@RequestMapping("/api/users/me/creator-request")
@RequiredArgsConstructor
public class CreatorRequestController {

    private final CreatorRequestService creatorRequestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatorRequestResponse submit(@AuthenticationPrincipal Long userId,
                                         @Valid @RequestBody CreatorRequestSubmitRequest request) {
        return creatorRequestService.submit(userId, request.reason());
    }

    @GetMapping
    public CreatorRequestResponse mine(@AuthenticationPrincipal Long userId) {
        return creatorRequestService.getMine(userId);
    }
}
