package com.sparta.one_stop.global.audit;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 관리자/시스템 작업 감사 로그
 *
 * <p><b>범용 자산</b> — 포인트뿐 아니라 회원 정지, 환불, 권한 변경 등 모든 민감 작업에 재사용
 *
 * <p><b>특징</b>
 * <ul>
 *   <li>한 번 기록되면 절대 수정/삭제 불가 (DB 트리거로 보호)</li>
 *   <li>비동기 기록으로 본 트랜잭션 성능 영향 없음</li>
 *   <li>월 단위 파티셔닝 권장 (대용량 운영 시)</li>
 * </ul>
 */
@Entity
@Table(name = "admin_audit_log",
    indexes = {
        @Index(name = "idx_audit_action_time", columnList = "action, occurredAt DESC"),
        @Index(name = "idx_audit_resource_time", columnList = "targetResource, occurredAt DESC"),
        @Index(name = "idx_audit_result_time", columnList = "result, occurredAt DESC")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 액션 코드 — POINT_USE, POINT_REFUND, USER_SUSPEND 등 */
    @Column(nullable = false, length = 50)
    private String action;

    /** 대상 리소스 — Point, User, Order 등 */
    @Column(nullable = false, length = 30)
    private String targetResource;

    /** 호출된 메서드 시그니처 — 디버깅용 */
    @Column(length = 200)
    private String methodName;

    /** 호출 인자 — 민감정보 마스킹 처리됨 */
    @Column(length = 500)
    private String args;

    /** SUCCESS / FAILURE */
    @Column(nullable = false, length = 10)
    private String result;

    /** 실패 시 에러 상세 (성공 시 null) */
    @Column(length = 500)
    private String errorDetail;

    @Column(length = 45)
    private String clientIp;

    @Column(length = 255)
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AdminAuditLog(String action, String targetResource, String methodName,
                          String args, String result, String errorDetail,
                          String clientIp, String userAgent, LocalDateTime occurredAt) {
        this.action = action;
        this.targetResource = targetResource;
        this.methodName = methodName;
        this.args = args;
        this.result = result;
        this.errorDetail = errorDetail;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.occurredAt = occurredAt;
    }
}
