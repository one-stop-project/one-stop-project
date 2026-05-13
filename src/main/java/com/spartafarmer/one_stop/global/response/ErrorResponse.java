package com.spartafarmer.one_stop.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spartafarmer.one_stop.global.exception.ErrorCode;
import lombok.Builder;
import lombok.Getter;
import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 에러 응답 형식
 * {
 *   "success": false,
 *   "status": 400,
 *   "code": "ORDER_002",
 *   "message": "재고가 부족합니다",
 *   "detail": "상세 설명 (개발자용)",
 *   "errors": [...] (유효성 검증 실패 시),
 *   "timestamp": "2025-05-09T14:30:00.123",
 *   "path": "/api/orders"
 * }
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final boolean success = false;
    private final int status;
    private final String code;
    private final String message;
    private final String detail;
    private final List<FieldError> errors;
    private final String timestamp;
    private final String path;

    @Builder
    private ErrorResponse(int status, String code, String message,
                          String detail, List<FieldError> errors, String path) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.detail = detail;
        this.errors = errors;
        this.path = path;
        this.timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));
    }

    // 일반 에러 응답 생성
    public static ErrorResponse of(ErrorCode errorCode, String detail, String path) {
        return ErrorResponse.builder()
            .status(errorCode.getStatus().value())
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .detail(detail)
            .path(path)
            .build();
    }

    // 유효성 검증 에러 응답 생성
    public static ErrorResponse ofValidation(BindingResult bindingResult, String path) {
        List<FieldError> fieldErrors = bindingResult.getFieldErrors().stream()
            .map(error -> FieldError.builder()
                .field(error.getField())
                .value(error.getRejectedValue() != null
                    ? error.getRejectedValue().toString() : "")
                .reason(error.getDefaultMessage())
                .build())
            .collect(Collectors.toList());

        return ErrorResponse.builder()
            .status(400)
            .code(ErrorCode.COMMON_001.getCode())
            .message(ErrorCode.COMMON_001.getMessage())
            .errors(fieldErrors)
            .path(path)
            .build();
    }

    /**
     * 필드별 유효성 검증 에러
     */
    @Getter
    @Builder
    public static class FieldError {
        private final String field;
        private final String value;
        private final String reason;
    }
}
