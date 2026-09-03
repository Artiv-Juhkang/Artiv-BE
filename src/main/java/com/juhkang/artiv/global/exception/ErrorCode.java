package com.juhkang.artiv.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 용량이 너무 큽니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    DUPLICATE_POST_CATEGORY(HttpStatus.CONFLICT, "이미 등록된 카테고리입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    ADULT_ONLY(HttpStatus.FORBIDDEN, "성인만 열람할 수 있습니다."),
    // 온톨로지 액션 가드. 429 대신 409를 쓰는 이유는 이것이 요청 빈도 제한이 아니라
    // "이번 주에는 이미 보냈다"는 상태 충돌이기 때문이다.
    ACTION_THROTTLED(HttpStatus.CONFLICT, "이번 주에는 이미 보냈어요. 다음 주에 다시 시도할 수 있어요."),
    SEGMENT_TOO_SMALL(HttpStatus.FORBIDDEN, "대상이 5명 미만이면 개인 식별 위험이 있어 보낼 수 없어요."),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "이미지 파일이 올바르지 않습니다."),
    STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장에 실패했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
