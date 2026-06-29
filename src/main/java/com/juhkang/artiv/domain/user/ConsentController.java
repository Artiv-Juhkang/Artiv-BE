package com.juhkang.artiv.domain.user;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.artiv.domain.user.dto.ConsentResponse;
import com.juhkang.artiv.domain.user.dto.ConsentUpdateRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 본인 법적 동의 내역 조회·토글(설정 화면). */
@RestController
@RequestMapping("/api/users/me/consents")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    @GetMapping
    public List<ConsentResponse> myConsents(@AuthenticationPrincipal Long userId) {
        return consentService.getMine(userId);
    }

    @PatchMapping
    public List<ConsentResponse> update(@AuthenticationPrincipal Long userId,
                                        @Valid @RequestBody ConsentUpdateRequest request) {
        return consentService.update(userId, request.consents());
    }
}
