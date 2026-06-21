package com.juhkang.apptoon.domain.auth.dto;

import java.time.LocalDate;
import java.util.Map;

import com.juhkang.apptoon.domain.user.ConsentType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(max = 20) String nickname,
        @NotNull @Past LocalDate birthDate,
        Map<ConsentType, Boolean> consents
) {
}
