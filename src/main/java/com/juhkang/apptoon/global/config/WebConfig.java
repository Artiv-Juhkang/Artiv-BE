package com.juhkang.apptoon.global.config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 업로드된 회차 이미지를 정적 리소스로 서빙한다.
 * {@code /files/**} → {@code file:${app.storage.root}/} 매핑 (저장 경로 규약: {root}/{seriesId}/{episodeNo}/{order}.{ext}).
 *
 * 트레이드오프: permitAll 정적 서빙이라 URL 을 알면 비공개/성인물 이미지에도 인가 없이 접근할 수 있다.
 * 실제 문제화되면 컨트롤러 기반 인증 서빙으로 승격한다(현재 학습 범위상 보류).
 * 디렉터리 트래버설은 Spring 의 PathResourceResolver 가 location 하위로 제한해 차단한다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String storageRoot;

    public WebConfig(@Value("${app.storage.root:storage}") String storageRoot) {
        this.storageRoot = storageRoot;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(storageRoot).toAbsolutePath().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/files/**")
                .addResourceLocations(location);
    }

    /** 작가·관리자 콘솔 SPA 진입점. /admin → static/admin/index.html */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/admin").setViewName("forward:/admin/index.html");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
    }
}
