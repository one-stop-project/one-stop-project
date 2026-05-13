package com.spartafarmer.one_stop.global.exception;

import lombok.Getter;

/**
 * 비즈니스 로직 예외 처리를 위한 커스텀 예외 클래스
 * ErrorCode를 통해 HTTP 상태코드와 에러 메시지를 함께 관리
 *
 * 사용 예시:
 * throw new CustomException(ErrorCode.USER_NOT_FOUND);
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
