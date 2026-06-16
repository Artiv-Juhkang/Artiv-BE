package com.juhkang.apptoon.global.config;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.juhkang.apptoon.global.exception.ErrorCode;
import com.juhkang.apptoon.global.exception.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 인증되지 않은 요청이 보호된 자원에 접근할 때 401 + 통일된 에러 JSON 으로 응답한다.
 * (시큐리티 필터 단계에서 발생하므로 @RestControllerAdvice 가 아니라 여기서 처리.)
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(ErrorCode.UNAUTHORIZED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(ErrorCode.UNAUTHORIZED));
    }
}
