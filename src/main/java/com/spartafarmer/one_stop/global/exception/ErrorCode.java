package com.spartafarmer.one_stop.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 Enum
 * 도메인별로 에러 코드를 관리하여 일관성 있는 에러 응답 제공
 * 형식: {도메인}_{번호} (예: AUTH_001, ORDER_002)
 */
@Getter
public enum ErrorCode {

    // ===== Auth =====
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH_001", "이미 사용중인 이메일입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "AUTH_002", "비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_004", "만료된 토큰입니다."),
    SUSPENDED_USER(HttpStatus.FORBIDDEN, "AUTH_005", "정지된 계정입니다."),

    // ===== User =====
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_001", "존재하지 않는 회원입니다."),
    SAME_PASSWORD(HttpStatus.BAD_REQUEST, "MEMBER_002", "현재 비밀번호와 동일합니다."),

    // ===== Seller =====
    SELLER_NOT_FOUND(HttpStatus.NOT_FOUND, "SELLER_001", "존재하지 않는 판매자입니다."),
    SELLER_NOT_APPROVED(HttpStatus.FORBIDDEN, "SELLER_002", "승인되지 않은 판매자입니다."),

    // ===== Product =====
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_001", "존재하지 않는 상품입니다."),
    PRODUCT_NOT_APPROVED(HttpStatus.BAD_REQUEST, "PRODUCT_002", "판매중이지 않은 상품입니다."),
    PRODUCT_NOT_TEMP_SAVED(HttpStatus.BAD_REQUEST, "PRODUCT_003", "임시저장 상태의 상품만 승인 요청할 수 있습니다."),
    PRODUCT_HAS_ACTIVE_ORDER(HttpStatus.BAD_REQUEST, "PRODUCT_009", "진행중인 주문이 있는 상품은 삭제할 수 없습니다."),

    // ===== Cart =====
    CART_ITEM_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "CART_001", "판매중이지 않은 상품입니다."),
    CART_QUANTITY_INVALID(HttpStatus.BAD_REQUEST, "CART_002", "수량은 1개 이상 99개 이하여야 합니다."),
    CART_OWN_PRODUCT(HttpStatus.BAD_REQUEST, "CART_005", "본인 상품은 장바구니에 담을 수 없습니다."),

    // ===== Order =====
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_001", "존재하지 않는 주문입니다."),
    ORDER_OUT_OF_STOCK(HttpStatus.CONFLICT, "ORDER_002", "재고가 부족합니다."),
    ORDER_PRODUCT_UNAVAILABLE(HttpStatus.BAD_REQUEST, "ORDER_003", "판매중이지 않은 상품입니다."),
    ORDER_COUPON_UNAVAILABLE(HttpStatus.BAD_REQUEST, "ORDER_005", "사용할 수 없는 쿠폰입니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ORDER_007", "본인 주문만 조회할 수 있습니다."),
    ORDER_CANCEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "ORDER_008", "배송지시 이후에는 취소할 수 없습니다."),

    // ===== Payment =====
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT_001", "결제 금액이 일치하지 않습니다."),
    PAYMENT_ALREADY_PAID(HttpStatus.CONFLICT, "PAYMENT_002", "이미 결제된 주문입니다."),

    // ===== Point =====
    POINT_INSUFFICIENT(HttpStatus.BAD_REQUEST, "POINT_001", "포인트가 부족합니다."),

    // ===== Coupon =====
    COUPON_OUT_OF_STOCK(HttpStatus.CONFLICT, "COUPON_001", "쿠폰 수량이 소진되었습니다."),
    COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "COUPON_002", "이미 발급받은 쿠폰입니다."),
    COUPON_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "COUPON_003", "발급 기간이 아닙니다."),

    // ===== Delivery =====
    DELIVERY_STATUS_INVALID(HttpStatus.BAD_REQUEST, "SHIPPING_001", "허용되지 않는 배송 상태 변경입니다."),
    DELIVERY_INVOICE_REQUIRED(HttpStatus.BAD_REQUEST, "SHIPPING_003", "운송장 번호를 입력해주세요."),

    // ===== Review =====
    REVIEW_DELIVERY_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "REVIEW_001", "배송 완료 후 리뷰를 작성할 수 있습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "REVIEW_002", "이미 리뷰를 작성했습니다."),
    REVIEW_RATING_INVALID(HttpStatus.BAD_REQUEST, "REVIEW_003", "별점은 1~5 사이여야 합니다."),
    REVIEW_ACCESS_DENIED(HttpStatus.FORBIDDEN, "REVIEW_006", "본인 리뷰만 수정할 수 있습니다."),

    // ===== Subscription =====
    SUBSCRIPTION_ALREADY_ACTIVE(HttpStatus.CONFLICT, "SUBSCRIPTION_001", "이미 활성화된 구독이 있습니다."),

    // ===== Common =====
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "입력값이 올바르지 않습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "COMMON_002", "접근 권한이 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_003", "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
