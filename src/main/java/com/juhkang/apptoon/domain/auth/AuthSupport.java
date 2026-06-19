package com.juhkang.apptoon.domain.auth;

import org.springframework.security.core.Authentication;

import com.juhkang.apptoon.domain.user.Role;

/** 컨트롤러에서 현재 인증 주체의 역할을 판정하는 헬퍼. principal(userId)과 별개로 권한을 본다. */
public final class AuthSupport {

    private AuthSupport() {
    }

    public static boolean isAdmin(Authentication authentication) {
        String adminAuthority = "ROLE_" + Role.ADMIN.name();
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(adminAuthority));
    }
}
