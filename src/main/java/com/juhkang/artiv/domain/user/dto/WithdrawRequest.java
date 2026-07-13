package com.juhkang.artiv.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record WithdrawRequest(
        @NotBlank String password
) {
}
