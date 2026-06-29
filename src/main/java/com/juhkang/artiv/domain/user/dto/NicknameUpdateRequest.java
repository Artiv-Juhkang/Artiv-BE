package com.juhkang.artiv.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record NicknameUpdateRequest(
        // 한글·영문·숫자·_ 만 — 멘션 @닉네임 문자셋과 일치
        @NotBlank @Pattern(regexp = "[\\p{IsHangul}A-Za-z0-9_]{1,20}") String nickname
) {
}
