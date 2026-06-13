package com.juhkang.apptoon.global.exception;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.juhkang.apptoon.global.exception.ErrorResponse.FieldErrorDetail;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: {} - {}", errorCode.name(), e.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<FieldErrorDetail> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }
}
