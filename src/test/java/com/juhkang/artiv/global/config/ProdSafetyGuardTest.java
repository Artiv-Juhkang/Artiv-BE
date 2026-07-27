package com.juhkang.artiv.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 운영 기동 안전망 — 배포 시 가장 흔한 사고(프로파일 미지정으로 dev 시크릿·CORS 전체허용이
 * 그대로 운영에 나가는 것)를 문서가 아니라 코드로 막는지 검증한다.
 */
class ProdSafetyGuardTest {

    private static final String DEV_SECRET = "dev-only-insecure-jwt-secret-change-me-0123456789abcdef";
    private static final String REAL_SECRET = "a-real-production-secret-value-0123456789abcdef";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProdSafetyGuard.class)
            .withPropertyValues("spring.profiles.active=prod");

    @Test
    void 운영에서_커밋된_dev_시크릿이면_기동에_실패한다() {
        runner.withPropertyValues(
                        "jwt.secret=" + DEV_SECRET,
                        "app.cors.allowed-origins=https://artiv.example")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void 운영에서_CORS_전체허용이면_기동에_실패한다() {
        runner.withPropertyValues(
                        "jwt.secret=" + REAL_SECRET,
                        "app.cors.allowed-origins=*")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void 운영_설정이_갖춰지면_정상_기동한다() {
        runner.withPropertyValues(
                        "jwt.secret=" + REAL_SECRET,
                        "app.cors.allowed-origins=https://artiv.example,https://www.artiv.example")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }
}
