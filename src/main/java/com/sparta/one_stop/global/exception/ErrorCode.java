package com.sparta.one_stop.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 Enum
 * 도메인별로 에러 코드를 관리하여 일관성 있는 에러 응답 제공
 * 형식: {도메인}_{번호} (예: AUTH_001, ORDER_002)
 */
@Getter
public enum ErrorCode {

    // ===== COMMON - 공통 =====
    COMMON_001(HttpStatus.BAD_REQUEST, "COMMON_001", "입력값이 올바르지 않습니다"),
    COMMON_002(HttpStatus.BAD_REQUEST, "COMMON_002", "요청 형식이 올바르지 않습니다"),
    COMMON_003(HttpStatus.BAD_REQUEST, "COMMON_003", "지원하지 않는 HTTP 메서드입니다"),
    COMMON_004(HttpStatus.BAD_REQUEST, "COMMON_004", "필수 파라미터가 누락되었습니다"),
    COMMON_005(HttpStatus.BAD_REQUEST, "COMMON_005", "파일 크기가 제한을 초과했습니다"),
    COMMON_006(HttpStatus.BAD_REQUEST, "COMMON_006", "지원하지 않는 파일 형식입니다"),
    COMMON_007(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_007", "서버 내부 오류가 발생했습니다"),
    COMMON_008(HttpStatus.SERVICE_UNAVAILABLE, "COMMON_008", "서비스가 일시적으로 이용 불가합니다"),
    COMMON_009(HttpStatus.TOO_MANY_REQUESTS, "COMMON_009", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요"),
    COMMON_010(HttpStatus.BAD_REQUEST, "COMMON_010", "요청 파라미터 값이 올바르지 않습니다"),

    // ===== AUTH - 인증/인가 =====
    AUTH_001(HttpStatus.BAD_REQUEST, "AUTH_001", "이메일 형식이 올바르지 않습니다"),
    AUTH_002(HttpStatus.CONFLICT, "AUTH_002", "이미 가입된 이메일입니다"),
    AUTH_003(HttpStatus.BAD_REQUEST, "AUTH_003", "비밀번호 규칙에 맞지 않습니다"),
    AUTH_004(HttpStatus.UNAUTHORIZED, "AUTH_004", "이메일 또는 비밀번호가 일치하지 않습니다"),
    AUTH_005(HttpStatus.FORBIDDEN, "AUTH_005", "정지된 계정입니다. 고객센터에 문의해주세요"),
    AUTH_006(HttpStatus.FORBIDDEN, "AUTH_006", "탈퇴한 계정입니다"),
    AUTH_007(HttpStatus.UNAUTHORIZED, "AUTH_007", "로그인이 필요합니다"),
    AUTH_008(HttpStatus.UNAUTHORIZED, "AUTH_008", "인증 토큰이 만료되었습니다"),
    AUTH_009(HttpStatus.UNAUTHORIZED, "AUTH_009", "유효하지 않은 인증 토큰입니다"),
    AUTH_010(HttpStatus.UNAUTHORIZED, "AUTH_010", "갱신 토큰이 만료되었습니다. 다시 로그인해주세요"),
    AUTH_011(HttpStatus.FORBIDDEN, "AUTH_011", "접근 권한이 없습니다"),
    AUTH_012(HttpStatus.UNAUTHORIZED, "AUTH_012", "다른 기기에서 로그인되어 세션이 만료되었습니다"),

    // ===== AUTH - 로그인 횟수 제한 =====
    AUTH_013(HttpStatus.TOO_MANY_REQUESTS, "AUTH_013", "너무 많은 요청입니다. 잠시 후 다시 시도해주세요"),

    // ===== AUTH - 기기제한 =====
    AUTH_014(HttpStatus.UNAUTHORIZED, "AUTH_014", "최대 기기 수를 초과했습니다. 다른 기기에서 로그아웃해주세요"),
    AUTH_015(HttpStatus.UNAUTHORIZED, "AUTH_015", "다른 기기에서 로그인되어 자동으로 로그아웃되었습니다"),

    // ===== AUTH - OAuth2 =====
    AUTH_016(HttpStatus.CONFLICT, "AUTH_016", "이미 OAuth2 계정이 연결되어 있습니다"),
    AUTH_017(HttpStatus.BAD_REQUEST, "AUTH_017", "지원하지 않는 OAuth2 제공자입니다"),
    AUTH_018(HttpStatus.UNAUTHORIZED, "AUTH_018", "OAuth2 인증 처리에 실패했습니다"),
    AUTH_019(HttpStatus.CONFLICT, "AUTH_019", "이미 다른 방법으로 가입된 이메일입니다"),


    // ===== MEMBER - 회원 =====
    MEMBER_001(HttpStatus.NOT_FOUND, "MEMBER_001", "회원 정보를 찾을 수 없습니다"),
    MEMBER_002(HttpStatus.BAD_REQUEST, "MEMBER_002", "현재 비밀번호가 일치하지 않습니다"),
    MEMBER_003(HttpStatus.BAD_REQUEST, "MEMBER_003", "새 비밀번호는 기존 비밀번호와 다르게 설정해주세요"),
    MEMBER_004(HttpStatus.NOT_FOUND, "MEMBER_004", "배송지 정보를 찾을 수 없습니다"),
    MEMBER_005(HttpStatus.BAD_REQUEST, "MEMBER_005", "기본 배송지는 삭제할 수 없습니다"),
    MEMBER_006(HttpStatus.BAD_REQUEST, "MEMBER_006", "배송지는 최대 10개까지 등록할 수 있습니다"),

    // ===== MEMBER - 상태 전이 =====
    MEMBER_010(HttpStatus.BAD_REQUEST, "MEMBER_010", "이미 탈퇴한 회원입니다"),
    MEMBER_011(HttpStatus.BAD_REQUEST, "MEMBER_011", "탈퇴한 회원은 처리할 수 없습니다"),
    MEMBER_012(HttpStatus.BAD_REQUEST, "MEMBER_012", "정지 상태가 아닌 회원입니다"),

    // ===== PRODUCT - 상품 =====
    PRODUCT_001(HttpStatus.NOT_FOUND, "PRODUCT_001", "상품을 찾을 수 없습니다"),
    PRODUCT_002(HttpStatus.BAD_REQUEST, "PRODUCT_002", "현재 판매하지 않는 상품입니다"),
    PRODUCT_003(HttpStatus.BAD_REQUEST, "PRODUCT_003", "판매 가격은 100원 이상이어야 합니다"),
    PRODUCT_004(HttpStatus.BAD_REQUEST, "PRODUCT_004", "할인가는 정가보다 낮아야 합니다"),
    PRODUCT_005(HttpStatus.BAD_REQUEST, "PRODUCT_005", "상품 이미지는 최소 1장 이상 필요합니다"),
    PRODUCT_006(HttpStatus.BAD_REQUEST, "PRODUCT_006", "상품 이미지는 최대 10장까지 등록할 수 있습니다"),
    PRODUCT_007(HttpStatus.NOT_FOUND, "PRODUCT_007", "카테고리를 찾을 수 없습니다"),
    PRODUCT_008(HttpStatus.FORBIDDEN, "PRODUCT_008", "다른 판매자의 상품은 수정할 수 없습니다"),
    PRODUCT_009(HttpStatus.BAD_REQUEST, "PRODUCT_009", "주문 진행 중인 상품은 삭제할 수 없습니다"),
    PRODUCT_010(HttpStatus.BAD_REQUEST, "PRODUCT_010", "현재 상태에서는 수정할 수 없습니다"),
    PRODUCT_011(HttpStatus.NOT_FOUND, "PRODUCT_011", "상품 이미지를 찾을 수 없습니다"),
    PRODUCT_012(HttpStatus.BAD_REQUEST, "PRODUCT_012", "상품 카테고리는 최소 1개 이상 매핑되어야 합니다"),
    PRODUCT_013(HttpStatus.BAD_REQUEST, "PRODUCT_013", "상품 카테고리는 최대 3개까지 매핑할 수 있습니다"),
    PRODUCT_014(HttpStatus.BAD_REQUEST, "PRODUCT_014", "조회 페이지 크기가 허용 범위를 벗어났습니다"),
    PRODUCT_015(HttpStatus.BAD_REQUEST, "PRODUCT_015", "가격 범위가 올바르지 않습니다"),
    PRODUCT_016(HttpStatus.BAD_REQUEST, "PRODUCT_016", "상품 옵션 조합이 중복됩니다"),
    PRODUCT_017(HttpStatus.INTERNAL_SERVER_ERROR, "PRODUCT_017", "상품 반려 이력을 찾을 수 없습니다"),

    // ===== CATEGORY - 카테고리 =====
    CATEGORY_001(HttpStatus.NOT_FOUND, "CATEGORY_001", "카테고리를 찾을 수 없습니다"),
    CATEGORY_002(HttpStatus.CONFLICT, "CATEGORY_002", "동일 부모 아래 중복된 카테고리명입니다"),
    CATEGORY_003(HttpStatus.BAD_REQUEST, "CATEGORY_003", "카테고리 깊이는 최대 3단계입니다"),
    CATEGORY_004(HttpStatus.BAD_REQUEST, "CATEGORY_004", "상품이 매핑된 카테고리는 삭제할 수 없습니다"),

    // ===== CART - 장바구니 =====
    CART_001(HttpStatus.BAD_REQUEST, "CART_001", "장바구니에 담을 수 없는 상품입니다"),
    CART_002(HttpStatus.BAD_REQUEST, "CART_002", "수량은 1개 이상 99개 이하여야 합니다"),
    CART_003(HttpStatus.BAD_REQUEST, "CART_003", "장바구니에 담을 수 있는 상품은 최대 50개입니다"),
    CART_004(HttpStatus.NOT_FOUND, "CART_004", "장바구니에 해당 상품이 없습니다"),
    CART_005(HttpStatus.BAD_REQUEST, "CART_005", "본인의 상품은 장바구니에 담을 수 없습니다"),
    CART_006(HttpStatus.FORBIDDEN, "CART_006", "본인의 장바구니만 접근할 수 있습니다"),

    // ===== CART - 장바구니 엔티티 검증 =====
    CART_020(HttpStatus.BAD_REQUEST, "CART_020", "장바구니 소유자는 필수입니다"),
    CART_021(HttpStatus.BAD_REQUEST, "CART_021", "장바구니 정보는 필수입니다"),
    CART_022(HttpStatus.BAD_REQUEST, "CART_022", "상품 옵션 정보는 필수입니다"),
    CART_023(HttpStatus.BAD_REQUEST, "CART_023", "수량은 1 이상이어야 합니다"),
    CART_024(HttpStatus.BAD_REQUEST, "CART_024", "장바구니 최대 수량은 99개입니다"),
    CART_025(HttpStatus.BAD_REQUEST, "CART_025", "수량은 1 미만이 될 수 없습니다"),

    // ===== ORDER - 주문 =====
    ORDER_001(HttpStatus.BAD_REQUEST, "ORDER_001", "주문할 상품이 없습니다"),
    ORDER_002(HttpStatus.BAD_REQUEST, "ORDER_002", "재고가 부족합니다"),
    ORDER_003(HttpStatus.BAD_REQUEST, "ORDER_003", "판매하지 않는 상품이 포함되어 있습니다"),
    ORDER_004(HttpStatus.NOT_FOUND, "ORDER_004", "배송지 정보를 찾을 수 없습니다"),
    ORDER_005(HttpStatus.BAD_REQUEST, "ORDER_005", "사용할 수 없는 쿠폰입니다"),
    ORDER_006(HttpStatus.NOT_FOUND, "ORDER_006", "주문 정보를 찾을 수 없습니다"),
    ORDER_007(HttpStatus.FORBIDDEN, "ORDER_007", "본인의 주문만 조회할 수 있습니다"),
    ORDER_008(HttpStatus.BAD_REQUEST, "ORDER_008", "현재 상태에서는 취소할 수 없습니다"),
    ORDER_009(HttpStatus.CONFLICT, "ORDER_009", "주문 상태가 변경되어 처리할 수 없습니다"),
    ORDER_010(HttpStatus.BAD_REQUEST, "ORDER_010", "최소 주문 금액은 1,000원입니다"),
    ORDER_011(HttpStatus.BAD_REQUEST, "ORDER_011", "미승인 판매자의 상품은 주문할 수 없습니다"),
    ORDER_012(HttpStatus.BAD_REQUEST, "ORDER_012", "쿠폰 할인 금액과 사용 포인트의 합은 상품 금액을 초과할 수 없습니다"),
    ORDER_013(HttpStatus.CONFLICT, "ORDER_013", "주문 처리 중입니다. 잠시 후 다시 시도해주세요."),

    // ===== ORDER - 주문 상품 엔티티 검증 =====
    ORDER_020(HttpStatus.BAD_REQUEST, "ORDER_020", "주문 정보는 필수입니다"),
    ORDER_021(HttpStatus.BAD_REQUEST, "ORDER_021", "상품 옵션 정보는 필수입니다"),
    ORDER_022(HttpStatus.BAD_REQUEST, "ORDER_022", "판매자 정보는 필수입니다"),
    ORDER_023(HttpStatus.BAD_REQUEST, "ORDER_023", "상품명은 필수입니다"),
    ORDER_024(HttpStatus.BAD_REQUEST, "ORDER_024", "수량은 1 이상이어야 합니다"),
    ORDER_025(HttpStatus.BAD_REQUEST, "ORDER_025", "가격은 0원 이상이어야 합니다"),
    ORDER_026(HttpStatus.BAD_REQUEST, "ORDER_026", "주문 접수는 결제 대기 상태에서만 처리할 수 있습니다"),
    ORDER_027(HttpStatus.BAD_REQUEST, "ORDER_027", "주문 확정은 주문 접수 상태에서만 가능합니다"),
    ORDER_028(HttpStatus.BAD_REQUEST, "ORDER_028", "배송 시작은 주문 확정 상태에서만 가능합니다"),
    ORDER_029(HttpStatus.BAD_REQUEST, "ORDER_029", "배송 완료는 배송 중 상태에서만 가능합니다"),
    ORDER_030(HttpStatus.BAD_REQUEST, "ORDER_030", "주문 거절은 주문 접수 상태에서만 가능합니다"),
    ORDER_031(HttpStatus.BAD_REQUEST, "ORDER_031", "현재 상태에서는 주문 상품을 취소할 수 없습니다"),

    // ===== ORDER - 주문 취소/거절 이력 엔티티 검증 =====
    ORDER_032(HttpStatus.BAD_REQUEST, "ORDER_032", "처리 주체 유형은 필수입니다"),
    ORDER_033(HttpStatus.BAD_REQUEST, "ORDER_033", "취소/거절 유형은 필수입니다"),
    ORDER_034(HttpStatus.BAD_REQUEST, "ORDER_034", "처리자 ID는 필수입니다"),
    ORDER_035(HttpStatus.BAD_REQUEST, "ORDER_035", "해당 처리 주체는 actorId를 가질 수 없습니다"),
    ORDER_036(HttpStatus.BAD_REQUEST, "ORDER_036", "처리자 ID는 1 이상이어야 합니다"),
    ORDER_037(HttpStatus.BAD_REQUEST, "ORDER_037", "취소/거절 금액은 0원 이상이어야 합니다"),
    ORDER_038(HttpStatus.BAD_REQUEST, "ORDER_038", "복구 포인트는 0 이상이어야 합니다"),

    // ===== ORDER - 주문 엔티티 검증 =====
    ORDER_039(HttpStatus.BAD_REQUEST, "ORDER_039", "주문자는 필수입니다"),
    ORDER_040(HttpStatus.BAD_REQUEST, "ORDER_040", "총 주문 금액은 0원 이상이어야 합니다"),
    ORDER_041(HttpStatus.BAD_REQUEST, "ORDER_041", "총 할인 금액은 0원 이상이어야 합니다"),
    ORDER_042(HttpStatus.BAD_REQUEST, "ORDER_042", "최종 결제 금액은 0원 이상이어야 합니다"),
    ORDER_043(HttpStatus.BAD_REQUEST, "ORDER_043", "사용 포인트는 0 이상이어야 합니다"),
    ORDER_044(HttpStatus.BAD_REQUEST, "ORDER_044", "구독 할인 금액은 0원 이상이어야 합니다"),
    ORDER_045(HttpStatus.BAD_REQUEST, "ORDER_045", "수령인 이름은 필수입니다"),
    ORDER_046(HttpStatus.BAD_REQUEST, "ORDER_046", "수령인 연락처는 필수입니다"),
    ORDER_047(HttpStatus.BAD_REQUEST, "ORDER_047", "배송 주소는 필수입니다"),
    ORDER_048(HttpStatus.BAD_REQUEST, "ORDER_048", "배송비는 0원 이상이어야 합니다"),
    ORDER_049(HttpStatus.BAD_REQUEST, "ORDER_049", "주문 유형은 필수입니다"),
    ORDER_050(HttpStatus.BAD_REQUEST, "ORDER_050", "결제 대기 상태에서만 결제 완료 처리할 수 있습니다"),
    ORDER_051(HttpStatus.BAD_REQUEST, "ORDER_051", "이미 취소된 주문입니다"),

    // ===== PAYMENT - 결제 =====
    PAYMENT_001(HttpStatus.BAD_REQUEST, "PAYMENT_001", "이미 결제가 완료된 주문입니다"),
    PAYMENT_002(HttpStatus.BAD_REQUEST, "PAYMENT_002", "결제 금액이 주문 금액과 일치하지 않습니다"),
    PAYMENT_003(HttpStatus.CONFLICT, "PAYMENT_003", "이미 처리된 결제 요청입니다"),
    PAYMENT_004(HttpStatus.BAD_GATEWAY, "PAYMENT_004", "결제 처리 중 오류가 발생했습니다"),
    PAYMENT_005(HttpStatus.BAD_REQUEST, "PAYMENT_005", "취소할 수 없는 결제입니다"),
    PAYMENT_006(HttpStatus.BAD_REQUEST, "PAYMENT_006", "결제 대기 시간이 초과되었습니다"),
    PAYMENT_007(HttpStatus.BAD_REQUEST, "PAYMENT_007", "지원하지 않는 결제 수단입니다"),
    PAYMENT_008(HttpStatus.BAD_REQUEST, "PAYMENT_008", "취소된 주문은 결제할 수 없습니다"),
    PAYMENT_009(HttpStatus.NOT_FOUND, "PAYMENT_009", "결제 정보를 찾을 수 없습니다"),
    PAYMENT_010(HttpStatus.CONFLICT, "PAYMENT_010", "결제 처리 중입니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_011(HttpStatus.BAD_REQUEST, "PAYMENT_011", "결제 대기 상태의 주문만 결제할 수 있습니다."),

    // ===== PAYMENT - 결제 엔티티 검증 =====
    PAYMENT_020(HttpStatus.BAD_REQUEST, "PAYMENT_020", "주문 정보는 필수입니다"),
    PAYMENT_021(HttpStatus.BAD_REQUEST, "PAYMENT_021", "결제 키는 필수입니다"),
    PAYMENT_022(HttpStatus.BAD_REQUEST, "PAYMENT_022", "결제 금액은 0원 이상이어야 합니다"),
    PAYMENT_023(HttpStatus.BAD_REQUEST, "PAYMENT_023", "결제 수단은 필수입니다"),
    PAYMENT_024(HttpStatus.BAD_REQUEST, "PAYMENT_024", "결제 대기 상태에서만 승인할 수 있습니다"),
    PAYMENT_025(HttpStatus.BAD_REQUEST, "PAYMENT_025", "결제 대기 상태에서만 실패 처리할 수 있습니다"),
    PAYMENT_026(HttpStatus.BAD_REQUEST, "PAYMENT_026", "이미 취소된 결제입니다"),
    PAYMENT_027(HttpStatus.BAD_REQUEST, "PAYMENT_027", "결제 완료 상태에서만 취소할 수 있습니다"),

    // ===== OUTBOX - 이벤트 발행 =====
    OUTBOX_001(HttpStatus.CONFLICT, "OUTBOX_001", "이미 저장된 Outbox 이벤트입니다"),
    OUTBOX_002(HttpStatus.NOT_FOUND, "OUTBOX_002", "Outbox 이벤트를 찾을 수 없습니다"),
    OUTBOX_003(HttpStatus.BAD_REQUEST, "OUTBOX_003", "처리할 수 없는 Outbox 이벤트 상태입니다"),

    // ===== OUTBOX - 이벤트 엔티티 검증 =====
    OUTBOX_020(HttpStatus.BAD_REQUEST, "OUTBOX_020", "이벤트 ID는 필수입니다"),
    OUTBOX_021(HttpStatus.BAD_REQUEST, "OUTBOX_021", "이벤트 타입은 필수입니다"),
    OUTBOX_022(HttpStatus.BAD_REQUEST, "OUTBOX_022", "Aggregate Type은 필수입니다"),
    OUTBOX_023(HttpStatus.BAD_REQUEST, "OUTBOX_023", "Aggregate ID는 필수입니다"),
    OUTBOX_024(HttpStatus.BAD_REQUEST, "OUTBOX_024", "Kafka Topic은 필수입니다"),
    OUTBOX_025(HttpStatus.BAD_REQUEST, "OUTBOX_025", "Partition Key는 필수입니다"),
    OUTBOX_026(HttpStatus.BAD_REQUEST, "OUTBOX_026", "이벤트 Payload는 필수입니다"),
    OUTBOX_027(HttpStatus.BAD_REQUEST, "OUTBOX_027", "PENDING 상태의 이벤트만 PROCESSING 처리할 수 있습니다"),
    OUTBOX_028(HttpStatus.BAD_REQUEST, "OUTBOX_028", "PROCESSING 상태의 이벤트만 PUBLISHED 처리할 수 있습니다"),
    OUTBOX_029(HttpStatus.BAD_REQUEST, "OUTBOX_029", "PROCESSING 상태의 이벤트만 실패 처리할 수 있습니다"),
    OUTBOX_030(HttpStatus.BAD_REQUEST, "OUTBOX_030", "최대 재시도 횟수는 0 이상이어야 합니다"),

    // ===== NOTIFICATION - 알림 엔티티 검증 =====
    NOTIFICATION_020(HttpStatus.BAD_REQUEST, "NOTIFICATION_020", "알림 대상 사용자는 필수입니다"),
    NOTIFICATION_021(HttpStatus.BAD_REQUEST, "NOTIFICATION_021", "이벤트 ID는 필수입니다"),
    NOTIFICATION_022(HttpStatus.BAD_REQUEST, "NOTIFICATION_022", "알림 유형은 필수입니다"),
    NOTIFICATION_023(HttpStatus.BAD_REQUEST, "NOTIFICATION_023", "알림 제목은 필수입니다"),
    NOTIFICATION_024(HttpStatus.BAD_REQUEST, "NOTIFICATION_024", "알림 내용은 필수입니다"),

    // ===== POINT - 포인트 =====
    POINT_001(HttpStatus.NOT_FOUND, "POINT_001", "포인트 계정을 찾을 수 없습니다"),
    POINT_002(HttpStatus.BAD_REQUEST, "POINT_002", "포인트가 부족합니다"),
    POINT_003(HttpStatus.BAD_REQUEST, "POINT_003", "포인트 금액은 1 이상이어야 합니다"),
    POINT_004(HttpStatus.BAD_REQUEST, "POINT_004", "포인트 충전 금액은 1,000원 이상 1,000,000원 이하여야 합니다"),
    POINT_005(HttpStatus.CONFLICT, "POINT_005", "포인트 처리 중 충돌이 발생했습니다. 다시 시도해주세요"),
    POINT_006(HttpStatus.BAD_REQUEST, "POINT_006", "적립률은 null일 수 없습니다."),
    POINT_007(HttpStatus.BAD_REQUEST, "POINT_007", "적립률은 음수일 수 없습니다."),
    POINT_008(HttpStatus.BAD_REQUEST, "POINT_008", "적립률은 100%를 초과할 수 없습니다."),
    POINT_009(HttpStatus.BAD_REQUEST, "POINT_009", "안전상한선 초과"),
    POINT_010(HttpStatus.BAD_REQUEST,"POINT_010","해시 입력값 오류"),

    // ===== POINT - 포인트 엔티티 검증 =====
    POINT_020(HttpStatus.BAD_REQUEST, "POINT_020", "포인트 소유자는 필수입니다"),
    POINT_021(HttpStatus.BAD_REQUEST, "POINT_021", "포인트 소유자 ID는 필수입니다"),

    // ===== POINT - 포인트 이력 엔티티 검증 =====
    POINT_022(HttpStatus.BAD_REQUEST, "POINT_022", "포인트 계정은 필수입니다"),
    POINT_023(HttpStatus.BAD_REQUEST, "POINT_023", "포인트 사용자는 필수입니다"),
    POINT_024(HttpStatus.BAD_REQUEST, "POINT_024", "포인트 변동 금액은 0일 수 없습니다"),
    POINT_025(HttpStatus.BAD_REQUEST, "POINT_025", "잔여 포인트는 0 이상이어야 합니다"),
    POINT_026(HttpStatus.BAD_REQUEST, "POINT_026", "포인트 이력 유형은 필수입니다"),
    POINT_027(HttpStatus.BAD_REQUEST, "POINT_027", "적립/충전/복구 금액은 양수여야 합니다"),
    POINT_028(HttpStatus.BAD_REQUEST, "POINT_028", "생성 시 잔여 포인트는 변동 금액과 같아야 합니다"),
    POINT_029(HttpStatus.BAD_REQUEST, "POINT_029", "포인트 만료일은 필수입니다"),
    POINT_030(HttpStatus.BAD_REQUEST, "POINT_030", "사용/만료 금액은 음수여야 합니다"),
    POINT_031(HttpStatus.BAD_REQUEST, "POINT_031", "사용/만료 이력의 잔여 포인트는 0이어야 합니다"),
    POINT_032(HttpStatus.BAD_REQUEST, "POINT_032", "차감 가능한 포인트 이력이 아닙니다"),
    POINT_033(HttpStatus.BAD_REQUEST, "POINT_033", "잔여 포인트가 부족합니다"),
    POINT_034(HttpStatus.BAD_REQUEST, "POINT_034", "만료 가능한 포인트 이력이 아닙니다"),

    // ===== POINT - 포인트 사용 상세 엔티티 검증 =====
    POINT_035(HttpStatus.BAD_REQUEST, "POINT_035", "포인트 사용 이력은 필수입니다"),
    POINT_036(HttpStatus.BAD_REQUEST, "POINT_036", "차감 대상 포인트 이력은 필수입니다"),
    POINT_037(HttpStatus.BAD_REQUEST, "POINT_037", "USE 이력만 사용 상세 이력과 연결할 수 있습니다"),
    POINT_038(HttpStatus.BAD_REQUEST, "POINT_038", "차감 대상은 CHARGE/EARN/REFUND 이력이어야 합니다"),
    POINT_039(HttpStatus.BAD_REQUEST, "POINT_039", "차감 금액은 1 이상이어야 합니다"),
    POINT_040(HttpStatus.BAD_REQUEST, "POINT_040", "차감 금액은 원본 이력의 잔여 포인트를 초과할 수 없습니다"),
    POINT_041(HttpStatus.BAD_REQUEST, "POINT_041", "원본 포인트 만료일은 필수입니다"),

    // ===== SHIPPING - 배송 =====
    SHIPPING_001(HttpStatus.BAD_REQUEST, "SHIPPING_001", "주문 확인 전에는 배송을 시작할 수 없습니다"),
    SHIPPING_002(HttpStatus.BAD_REQUEST, "SHIPPING_002", "유효하지 않은 배송 상태 변경입니다"),
    SHIPPING_003(HttpStatus.BAD_REQUEST, "SHIPPING_003", "송장 번호는 필수 입력값입니다"),
    SHIPPING_004(HttpStatus.BAD_REQUEST, "SHIPPING_004", "지원하지 않는 택배사입니다"),
    SHIPPING_005(HttpStatus.NOT_FOUND, "SHIPPING_005", "배송 정보를 찾을 수 없습니다"),
    SHIPPING_006(HttpStatus.FORBIDDEN, "SHIPPING_006", "다른 판매자의 배송은 관리할 수 없습니다"),

    // ===== INVENTORY - 재고 =====
    INVENTORY_001(HttpStatus.BAD_REQUEST, "INVENTORY_001", "재고가 부족합니다"),
    INVENTORY_002(HttpStatus.BAD_REQUEST, "INVENTORY_002", "입고 수량은 1개 이상이어야 합니다"),
    INVENTORY_003(HttpStatus.BAD_REQUEST, "INVENTORY_003", "입고 수량이 최대 한도를 초과합니다"),
    INVENTORY_004(HttpStatus.NOT_FOUND, "INVENTORY_004", "재고 정보를 찾을 수 없습니다"),
    INVENTORY_005(HttpStatus.BAD_REQUEST, "INVENTORY_005", "보정 재고가 최대 한도를 초과합니다"),

    // ===== COUPON - 쿠폰 =====
    COUPON_001(HttpStatus.BAD_REQUEST, "COUPON_001", "쿠폰이 모두 소진되었습니다"),
    COUPON_002(HttpStatus.CONFLICT, "COUPON_002", "이미 다운로드한 쿠폰입니다"),
    COUPON_003(HttpStatus.BAD_REQUEST, "COUPON_003", "쿠폰 발급 기간이 아닙니다"),
    COUPON_004(HttpStatus.NOT_FOUND, "COUPON_004", "쿠폰을 찾을 수 없습니다"),
    COUPON_005(HttpStatus.BAD_REQUEST, "COUPON_005", "이 쿠폰은 최소 주문 금액 미달입니다"),
    COUPON_006(HttpStatus.BAD_REQUEST, "COUPON_006", "이미 사용한 쿠폰입니다"),
    COUPON_007(HttpStatus.BAD_REQUEST, "COUPON_007", "만료된 쿠폰입니다"),
    COUPON_008(HttpStatus.BAD_REQUEST, "COUPON_008", "할인 값이 유효하지 않습니다"),
    COUPON_009(HttpStatus.INTERNAL_SERVER_ERROR, "COUPON_009", "지원하지 않는 쿠폰 발급 전략입니다."),
    COUPON_010(HttpStatus.BAD_REQUEST, "COUPON_010", "이미 비활성화된 쿠폰입니다"),
    COUPON_011(HttpStatus.NOT_FOUND, "COUPON_011", "사용자 쿠폰을 찾을 수 없습니다"),
    COUPON_012(HttpStatus.SERVICE_UNAVAILABLE, "COUPON_012", "쿠폰 발급 처리 중 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요"),

    // ===== COUPON - 쿠폰 엔티티 검증 =====
    COUPON_020(HttpStatus.BAD_REQUEST, "COUPON_020", "쿠폰명은 필수입니다"),
    COUPON_021(HttpStatus.BAD_REQUEST, "COUPON_021", "쿠폰 할인 타입은 필수입니다"),
    COUPON_022(HttpStatus.BAD_REQUEST, "COUPON_022", "쿠폰 할인 값은 1 이상이어야 합니다"),
    COUPON_023(HttpStatus.BAD_REQUEST, "COUPON_023", "최소 주문 금액은 0원 이상이어야 합니다"),
    COUPON_024(HttpStatus.BAD_REQUEST, "COUPON_024", "쿠폰 발급 가능 총 수량은 1 이상이어야 합니다"),
    COUPON_025(HttpStatus.BAD_REQUEST, "COUPON_025", "쿠폰 시작일은 필수입니다"),
    COUPON_026(HttpStatus.BAD_REQUEST, "COUPON_026", "쿠폰 만료일은 필수입니다"),
    COUPON_027(HttpStatus.BAD_REQUEST, "COUPON_027", "쿠폰 만료일은 시작일 이후여야 합니다"),
    COUPON_028(HttpStatus.BAD_REQUEST, "COUPON_028", "정률 쿠폰 할인율은 100 이하이어야 합니다"),
    COUPON_029(HttpStatus.BAD_REQUEST, "COUPON_029", "정률 쿠폰은 최대 할인 금액이 필수입니다"),
    COUPON_030(HttpStatus.BAD_REQUEST, "COUPON_030", "정액 쿠폰은 최대 할인 금액을 사용할 수 없습니다"),
    COUPON_031(HttpStatus.BAD_REQUEST, "COUPON_031", "주문 금액은 0원 이상이어야 합니다"),
    COUPON_032(HttpStatus.BAD_REQUEST, "COUPON_032", "현재 시각은 필수입니다"),
    COUPON_033(HttpStatus.BAD_REQUEST, "COUPON_033", "비활성 쿠폰은 발급할 수 없습니다"),

    // ===== COUPON - 사용자 쿠폰 엔티티 검증 =====
    COUPON_034(HttpStatus.BAD_REQUEST, "COUPON_034", "쿠폰 소유자는 필수입니다"),
    COUPON_035(HttpStatus.BAD_REQUEST, "COUPON_035", "쿠폰 정보는 필수입니다"),
    COUPON_036(HttpStatus.BAD_REQUEST, "COUPON_036", "쿠폰을 사용할 주문 정보는 필수입니다"),
    COUPON_037(HttpStatus.BAD_REQUEST, "COUPON_037", "쿠폰 사용 일시는 필수입니다"),
    COUPON_038(HttpStatus.BAD_REQUEST, "COUPON_038", "사용 가능한 쿠폰이 아닙니다"),
    COUPON_039(HttpStatus.BAD_REQUEST, "COUPON_039", "사용 완료된 쿠폰만 복구할 수 있습니다"),
    COUPON_040(HttpStatus.BAD_REQUEST, "COUPON_040", "이미 사용된 쿠폰은 만료 처리할 수 없습니다"),
    COUPON_041(HttpStatus.BAD_REQUEST, "COUPON_041", "아직 만료되지 않은 쿠폰입니다"),
    COUPON_042(HttpStatus.BAD_REQUEST, "COUPON_042", "사용자 ID는 필수입니다"),
    COUPON_043(HttpStatus.FORBIDDEN, "COUPON_043", "본인 쿠폰만 사용할 수 있습니다"),

    // ===== REVIEW - 리뷰 =====
    REVIEW_001(HttpStatus.BAD_REQUEST, "REVIEW_001", "배송 완료 후에만 리뷰를 작성할 수 있습니다"),
    REVIEW_002(HttpStatus.CONFLICT, "REVIEW_002", "이미 리뷰를 작성한 주문 상품입니다"),
    REVIEW_003(HttpStatus.BAD_REQUEST, "REVIEW_003", "별점은 1점에서 5점 사이여야 합니다"),
    REVIEW_004(HttpStatus.BAD_REQUEST, "REVIEW_004", "리뷰 내용은 10자 이상 1,000자 이하여야 합니다"),
    REVIEW_005(HttpStatus.NOT_FOUND, "REVIEW_005", "리뷰를 찾을 수 없습니다"),
    REVIEW_006(HttpStatus.FORBIDDEN, "REVIEW_006", "본인의 리뷰만 수정/삭제할 수 있습니다"),
    REVIEW_007(HttpStatus.BAD_REQUEST, "REVIEW_007", "작성 후 30일이 지나 수정할 수 없습니다"),
    REVIEW_008(HttpStatus.BAD_REQUEST, "REVIEW_008", "리뷰 이미지는 최대 5개까지 등록할 수 있습니다"),

    // ===== SUBSCRIPTION - 구독 =====
    SUBSCRIPTION_001(HttpStatus.NOT_FOUND, "SUBSCRIPTION_001", "구독 정보를 찾을 수 없습니다"),
    SUBSCRIPTION_002(HttpStatus.CONFLICT, "SUBSCRIPTION_002", "이미 유효한 구독이 존재합니다"),
    SUBSCRIPTION_003(HttpStatus.BAD_REQUEST, "SUBSCRIPTION_003", "구독이 이미 해지되었습니다"),
    SUBSCRIPTION_004(HttpStatus.BAD_REQUEST, "SUBSCRIPTION_004", "현재 상태에서는 구독을 변경할 수 없습니다"),
    SUBSCRIPTION_005(HttpStatus.BAD_REQUEST, "SUBSCRIPTION_005", "자동 결제에 실패했습니다"),
    SUBSCRIPTION_007(HttpStatus.FORBIDDEN, "SUBSCRIPTION_007", "본인의 구독만 조회할 수 있습니다"),
    SUBSCRIPTION_008(HttpStatus.FORBIDDEN, "SUBSCRIPTION_008", "본인의 구독만 수정할 수 있습니다"),
    SUBSCRIPTION_009(HttpStatus.BAD_REQUEST, "SUBSCRIPTION_009", "다음 결제일이 유효하지 않습니다"),
    SUBSCRIPTION_010(HttpStatus.BAD_REQUEST, "SUBSCRIPTION_010", "구독 상태가 변경되어 처리할 수 없습니다"),
    SUBSCRIPTION_011(HttpStatus.BAD_REQUEST, "SUBSCRIPTION_011", "이미 만료된 구독입니다"),

    // ===== SELLER - 판매자 =====
    SELLER_001(HttpStatus.NOT_FOUND, "SELLER_001", "판매자 정보를 찾을 수 없습니다"),
    SELLER_002(HttpStatus.CONFLICT, "SELLER_002", "이미 입점 신청이 완료되었습니다"),
    SELLER_003(HttpStatus.FORBIDDEN, "SELLER_003", "판매자 승인이 필요합니다"),
    SELLER_004(HttpStatus.FORBIDDEN, "SELLER_004", "정지된 판매자 계정입니다"),
    SELLER_005(HttpStatus.BAD_REQUEST, "SELLER_005", "사업자등록번호 형식이 올바르지 않습니다"),
    SELLER_006(HttpStatus.CONFLICT, "SELLER_006", "이미 등록된 사업자등록번호입니다"),
    SELLER_007(HttpStatus.FORBIDDEN, "SELLER_007", "해당 주문 상품의 판매자가 아닙니다"),
    SELLER_008(HttpStatus.BAD_REQUEST, "SELLER_008", "이미 처리된 주문입니다"),
    SELLER_010(HttpStatus.BAD_REQUEST, "SELLER_010", "판매자 상호명은 필수입니다"),
    SELLER_011(HttpStatus.BAD_REQUEST, "SELLER_011", "사업자 등록번호는 필수입니다"),
    SELLER_012(HttpStatus.BAD_REQUEST, "SELLER_012", "은행명은 필수입니다"),
    SELLER_013(HttpStatus.BAD_REQUEST, "SELLER_013", "계좌번호는 필수입니다"),

    // ===== AI - AI 서비스 =====
    AI_001(HttpStatus.SERVICE_UNAVAILABLE, "AI_001", "AI 서비스가 일시적으로 이용 불가합니다. 잠시 후 다시 시도해주세요"),

    // ===== ADMIN - 관리자 =====
    ADMIN_001(HttpStatus.FORBIDDEN, "ADMIN_001", "관리자 권한이 필요합니다"),
    ADMIN_002(HttpStatus.BAD_REQUEST, "ADMIN_002", "이미 승인된 판매자입니다"),
    ADMIN_003(HttpStatus.BAD_REQUEST, "ADMIN_003", "이미 거절된 판매자입니다"),
    ADMIN_004(HttpStatus.BAD_REQUEST, "ADMIN_004", "관리자 계정은 정지할 수 없습니다"),
    ADMIN_005(HttpStatus.BAD_REQUEST, "ADMIN_005", "하위 카테고리가 존재하여 삭제할 수 없습니다"),
    ADMIN_006(HttpStatus.BAD_REQUEST, "ADMIN_006", "상품이 등록된 카테고리는 삭제할 수 없습니다"),
    ADMIN_007(HttpStatus.BAD_REQUEST, "ADMIN_007", "이미 강제 비활성화된 상품입니다"),
    ADMIN_008(HttpStatus.BAD_REQUEST, "ADMIN_008", "이미 승인된 상품입니다"),             // 추가
    ADMIN_009(HttpStatus.BAD_REQUEST, "ADMIN_009", "이미 반려된 상품입니다"),
    ADMIN_010(HttpStatus.BAD_REQUEST, "ADMIN_010", "정지 상태인 판매자가 아닙니다"),
    ADMIN_011(HttpStatus.BAD_REQUEST, "ADMIN_011", "판매자 계정에는 관리자 권한을 부여할 수 없습니다"),
    ADMIN_012(HttpStatus.BAD_REQUEST, "ADMIN_012", "이미 관리자 권한이 있는 계정입니다"),
    ADMIN_013(HttpStatus.FORBIDDEN, "ADMIN_013", "SUPER_ADMIN 계정의 권한은 회수할 수 없습니다"),
    ADMIN_014(HttpStatus.FORBIDDEN, "ADMIN_014", "본인 계정의 권한은 회수할 수 없습니다"),
    ADMIN_015(HttpStatus.BAD_REQUEST, "ADMIN_015", "관리자 권한이 없는 계정입니다"),
    ADMIN_016(HttpStatus.BAD_REQUEST, "ADMIN_016", "비활성(정지/탈퇴) 상태의 회원은 관리자로 승격할 수 없습니다."),
    ADMIN_017(HttpStatus.BAD_REQUEST, "ADMIN_017", "승인 요청 상태의 상품만 승인/반려할 수 있습니다");
    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
