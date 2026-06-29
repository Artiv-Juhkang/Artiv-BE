package com.juhkang.artiv.domain.admin.dto;

import com.juhkang.artiv.domain.user.Role;

import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(
        @NotNull Role role
) {
}
