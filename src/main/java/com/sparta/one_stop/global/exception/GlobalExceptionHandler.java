package com.sparta.one_stop.global.exception;

import com.sparta.one_stop.global.response.ErrorResponse;
import jakarta.persistence.LockTimeoutException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
    public ResponseEntity<ErrorResponse> handleCustomException(
        CustomException e, HttpServletRequest request) {
        log.error("CustomException: {} - {}", e.getErrorCode().getCode(), e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
            .status(errorCode.getStatus())
            .body(ErrorResponse.of(errorCode, e.getDetail(), request.getRequestURI()));
    }

    /**
     * @Valid 유효성 검증 실패 처리
     * 필드별 에러 메시지 목록 반환
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidException(
        MethodArgumentNotValidException e, HttpServletRequest request) {
        log.error("ValidationException: {}", e.getMessage());
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.ofValidation(e.getBindingResult(), request.getRequestURI()));
    }

    /**
     * 낙관적 락 충돌 처리
     * 동시 요청으로 인한 상태 변경 충돌 시 ORDER_009 반환
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
        HttpServletRequest request) {
        log.error("OptimisticLockException: {}", request.getRequestURI());
        return ResponseEntity
            .status(ErrorCode.ORDER_009.getStatus())
            .body(ErrorResponse.of(ErrorCode.ORDER_009, null, request.getRequestURI()));
    }

    /**
     * 비관적 락 획득 실패 / 락 타임아웃 처리
     * 동일 주문에 대한 동시 처리 요청이 진행 중이면 ORDER_013 반환
     */
    @ExceptionHandler({
        PessimisticLockingFailureException.class,
        CannotAcquireLockException.class,
        LockTimeoutException.class
    })
    public ResponseEntity<ErrorResponse> handlePessimisticLockException(
        Exception e,
        HttpServletRequest request
    ) {
        log.warn(
            "LockAcquireException: {} - {}",
            request.getRequestURI(),
            e.getMessage()
        );

        return ResponseEntity
            .status(ErrorCode.ORDER_013.getStatus())
            .body(ErrorResponse.of(
                ErrorCode.ORDER_013,
                null,
                request.getRequestURI()
            ));
    }

    /**
     * 지원하지 않는 HTTP 메서드 처리
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
        HttpServletRequest request) {
        log.error("MethodNotSupportedException: {}", request.getRequestURI());
        return ResponseEntity
            .status(ErrorCode.COMMON_003.getStatus())
            .body(ErrorResponse.of(ErrorCode.COMMON_003, null, request.getRequestURI()));
    }

    /**
     * 필수 파라미터 누락 처리
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParams(
        MissingServletRequestParameterException e, HttpServletRequest request) {
        log.error("MissingParamException: {}", e.getParameterName());
        return ResponseEntity
            .status(ErrorCode.COMMON_004.getStatus())
            .body(ErrorResponse.of(ErrorCode.COMMON_004, null, request.getRequestURI()));
    }

    /**
     * 파일 크기 초과 처리
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(
        HttpServletRequest request) {
        log.error("MaxUploadSizeExceededException: {}", request.getRequestURI());
        return ResponseEntity
            .status(ErrorCode.COMMON_005.getStatus())
            .body(ErrorResponse.of(ErrorCode.COMMON_005, null, request.getRequestURI()));
    }

    /**
     * 그 외 예상치 못한 예외 처리
     * 500 Internal Server Error 반환
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
        Exception e, HttpServletRequest request) {
        log.error("Exception: {} - {}", request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity
            .status(ErrorCode.COMMON_007.getStatus())
            .body(ErrorResponse.of(ErrorCode.COMMON_007, null, request.getRequestURI()));
    }
}
