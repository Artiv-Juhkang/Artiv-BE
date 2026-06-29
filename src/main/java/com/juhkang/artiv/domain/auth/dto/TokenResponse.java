package com.juhkang.artiv.domain.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}
