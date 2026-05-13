package com.spartafarmer.one_stop.global.exception;

import com.spartafarmer.one_stop.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리 핸들러
 * 모든 Controller에서 발생하는 예외를 한 곳에서 처리
 * 일관된 에러 응답 형식 보장
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 로직 예외 처리
     * CustomException 발생 시 ErrorCode에 정의된 상태코드와 메시지 반환
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        log.error("CustomException: {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
            .status(errorCode.getStatus())
            .body(ApiResponse.fail(e.getMessage()));
    }

    /**
     * @Valid 유효성 검증 실패 처리
     * Request DTO의 @NotBlank, @NotNull 등 검증 실패 시 첫 번째 에러 메시지 반환
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "입력값이 올바르지 않습니다.";
        log.error("ValidationException: {}", message);
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.fail(message));
    }

    /**
     * 그 외 예상치 못한 예외 처리
     * 500 Internal Server Error 반환
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Exception: {}", e.getMessage());
        return ResponseEntity
            .internalServerError()
            .body(ApiResponse.fail("서버 오류가 발생했습니다."));
    }
}
