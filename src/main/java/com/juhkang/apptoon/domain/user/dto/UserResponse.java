package com.juhkang.apptoon.domain.user.dto;

import com.juhkang.apptoon.domain.user.Role;
import com.juhkang.apptoon.domain.user.User;

/**
 * 사용자 응답 DTO.
 * birthDate(생년월일)는 민감정보라 의도적으로 미노출 — /me와 관리자(changeUserRole)가 이 DTO를 공유하므로
 * 여기에 넣으면 관리자가 타인 생년월일을 보게 된다. 본인 프로필 표시가 필요해지면 /me 전용 응답으로 분리한다.
 */
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
