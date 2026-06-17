package com.juhkang.apptoon.domain.admin.dto;

import com.juhkang.apptoon.domain.user.Role;

import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(
        @NotNull Role role
) {
}
