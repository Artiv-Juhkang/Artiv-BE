package com.juhkang.apptoon.domain.user.dto;

import com.juhkang.apptoon.domain.user.Role;
import com.juhkang.apptoon.domain.user.User;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        Role role
) {

    public static UserResponse of(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole());
    }
}
