package com.spartafarmer.one_stop.global.exception;

import lombok.Getter;

/**
 * 비즈니스 로직 예외 처리를 위한 커스텀 예외 클래스
 * ErrorCode를 통해 HTTP 상태코드와 에러 메시지를 함께 관리
 * detail은 개발자용 상세 설명 (운영 환경에서 숨김 가능)
 *
 * 사용 예시:
 * throw new CustomException(ErrorCode.USER_NOT_FOUND);
 * throw new CustomException(ErrorCode.ORDER_002, "에어팟 프로 2세대 (잔여: 0개, 요청: 2개)");
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;

    // detail 없는 경우
    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = null;
    }

    // detail 있는 경우 (재고 부족 등 상세 정보 필요할 때)
    public CustomException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = detail;
    }
}
