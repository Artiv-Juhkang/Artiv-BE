package com.juhkang.artiv.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI appToonOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AppToon API")
                        .version("v1")
                        .description("웹툰 플랫폼 백엔드 REST API — 인증(JWT)·작품/회차·뷰어(19금 가드·이미지 정적 서빙)"
                                + "·개인화(구독·읽음·북마크)·소셜(댓글·좋아요)·조회수·성인전용 분류·관리자."))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
