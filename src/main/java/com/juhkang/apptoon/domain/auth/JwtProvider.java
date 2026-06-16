package com.juhkang.apptoon.domain.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.juhkang.apptoon.domain.user.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * 액세스 토큰(JWT) 발급·검증 전담.
 * 리프레시 토큰은 JWT가 아니라 DB에 저장되는 불투명 랜덤값이라 여기서 다루지 않는다({@link AuthService}).
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenValidity;

    public JwtProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = properties.accessTokenValidity();
    }

    public String createAccessToken(Long userId, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenValidity)))
                .signWith(key)
                .compact();
    }

    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getPayload().getSubject());
    }

    public Role getRole(String token) {
        String role = parse(token).getPayload().get("role", String.class);
        return role == null ? null : Role.valueOf(role);
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }
}
