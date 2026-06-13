package com.juhkang.apptoon.domain.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.juhkang.apptoon.domain.user.Role;

class JwtProviderTest {

    private final JwtProvider jwtProvider = new JwtProvider(
            new JwtProperties("test-secret-key-that-is-at-least-32-bytes-long-1234567890", 3600000L, 1209600000L));

    @Test
    void 액세스_토큰을_발급하고_사용자ID와_역할을_복원한다() {
        String token = jwtProvider.createAccessToken(42L, Role.CREATOR);

        assertTrue(jwtProvider.isValid(token));
        assertEquals(42L, jwtProvider.getUserId(token));
        assertEquals(Role.CREATOR, jwtProvider.getRole(token));
    }

    @Test
    void 다른_키로_서명되거나_형식이_잘못된_토큰은_유효하지_않다() {
        JwtProvider other = new JwtProvider(
                new JwtProperties("a-totally-different-secret-key-0987654321-abcdefghij", 3600000L, 1209600000L));
        String foreignToken = other.createAccessToken(1L, Role.READER);

        assertFalse(jwtProvider.isValid(foreignToken));    // 서명 키가 다름
        assertFalse(jwtProvider.isValid("garbage.token"));  // 형식 오류
    }
}
