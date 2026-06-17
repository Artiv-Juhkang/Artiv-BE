package com.juhkang.apptoon.domain.personalization;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juhkang.apptoon.domain.personalization.dto.SubscriptionResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PersonalizationController {

    private final PersonalizationService personalizationService;

    @PostMapping("/api/series/{seriesId}/subscription")
    @ResponseStatus(HttpStatus.CREATED)
    public void subscribe(@AuthenticationPrincipal Long userId, @PathVariable Long seriesId) {
        personalizationService.subscribe(userId, seriesId);
    }

    @DeleteMapping("/api/series/{seriesId}/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@AuthenticationPrincipal Long userId, @PathVariable Long seriesId) {
        personalizationService.unsubscribe(userId, seriesId);
    }

    @PostMapping("/api/series/{seriesId}/episodes/{episodeNo}/read")
    @ResponseStatus(HttpStatus.CREATED)
    public void read(@AuthenticationPrincipal Long userId,
                     @PathVariable Long seriesId,
                     @PathVariable int episodeNo) {
        personalizationService.markRead(userId, seriesId, episodeNo);
    }

    @GetMapping("/api/me/subscriptions")
    public List<SubscriptionResponse> mySubscriptions(@AuthenticationPrincipal Long userId) {
        return personalizationService.getMySubscriptions(userId);
    }
}
