package com.juhkang.artiv.global.exception;

import java.util.List;

public record ErrorResponse(
        int status,
        String code,
        String message,
        List<FieldErrorDetail> fieldErrors
) {

    public record FieldErrorDetail(String field, String reason) {
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorDetail> fieldErrors) {
        return new ErrorResponse(
                errorCode.getStatus().value(),
                errorCode.name(),
                errorCode.getMessage(),
                fieldErrors
        );
    }
}
