package com.juhkang.apptoon.domain.auth.dto;

import java.time.LocalDate;
import java.util.Map;

import com.juhkang.apptoon.domain.user.ConsentType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        // 한글·영문·숫자·_ 만 — 멘션 @닉네임 문자셋과 일치(모든 유효 닉네임은 멘션 가능해야 함)
        @NotBlank @Pattern(regexp = "[\\p{IsHangul}A-Za-z0-9_]{1,20}") String nickname,
        @NotNull @Past LocalDate birthDate,
        Map<ConsentType, Boolean> consents
) {
}
