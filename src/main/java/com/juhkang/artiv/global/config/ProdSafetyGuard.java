package com.juhkang.artiv.global.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;

/**
 * 운영(prod) 기동 전 안전망.
 * ------------------------------------------------------------------
 * 개발 편의 기본값이 그대로 운영에 나가는 사고를 배포 가이드(문서)가 아니라 기동 실패로 막는다.
 * 두 값 모두 dev 프로파일에는 편의 기본값이 있어(커밋된 JWT 시크릿 · CORS 전체허용) 프로파일만
 * 잘못 지정해도 조용히 위험한 상태로 뜬다 — 그래서 prod에서만 명시적으로 거부한다.
 *
 * 헬스체크는 이미 `/api/health`가 있어 actuator는 도입하지 않는다.
 */
@Configuration
@Profile("prod")
public class ProdSafetyGuard {

    /** application-dev.yml에 커밋된 개발 전용 시크릿. 운영에 이 값이 오면 즉시 중단. */
    private static final String DEV_JWT_SECRET = "dev-only-insecure-jwt-secret-change-me-0123456789abcdef";

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${app.cors.allowed-origins:}")
    private List<String> allowedOrigins;

    @PostConstruct
    void verify() {
        if (jwtSecret == null || jwtSecret.isBlank() || DEV_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "운영 기동 거부: JWT_SECRET이 비었거나 개발용 기본값입니다. 운영 전용 시크릿을 환경변수로 지정하세요.");
        }
        if (allowedOrigins == null || allowedOrigins.isEmpty() || allowedOrigins.contains("*")) {
            throw new IllegalStateException(
                    "운영 기동 거부: CORS_ALLOWED_ORIGINS가 비었거나 전체 허용(*)입니다. 프론트 origin을 명시하세요.");
        }
    }
}
