package com.sparta.one_stop.global.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 보안 감사 어노테이션
 *
 * 이 어노테이션이 붙은 메서드는 AOP가 자동으로 호출/성공/실패를 기록합니다.
 *
 * 사용 예시
 *
 * Audited(SELLER_PRODUCT_DELETE)
 * Transactional
 * public ProductDeleteResponse delete(Long userId, Long productId) {
 *     // 성공 시 → SELLER_PRODUCT_DELETE / SUCCESS 기록
 *     // 예외 시 → SELLER_PRODUCT_DELETE / FAILURE + 에러 정보 기록
 * }
 *
 * // 대상 리소스 ID를 자동 추출하려면 SpEL 사용 가능
 * Audited(value = SELLER_PRODUCT_UPDATE, targetIdExpr = "#productId")
 * public void update(Long userId, Long productId, ...) { }
 *
 *
 *
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** 이벤트 유형 (필수) */
    SecurityAuditEventType value();

    /** 대상 리소스 종류 — 예: "Product", "User", "Point" */
    String targetResource() default "";

    /**
     * 대상 리소스 ID를 추출할 SpEL 표현식
     *
     * 예: {@code "#productId"}, {@code "#request.userId"}
     * 비어있으면 ID 기록 안 함.
     */
    String targetIdExpr() default "";

    /**
     * 인자 기록 여부 — 민감 정보(비밀번호 등) 있으면 false로
     */
    boolean recordArgs() default true;
}
