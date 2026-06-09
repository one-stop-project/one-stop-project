package com.sparta.one_stop.global.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 통합 보안 감사 로그 — 인증·권한·관리자·도메인 보안 이벤트 통합
 *
 * 설계 의도
 *
 *   한 엔티티로 모든 보안 이벤트 통합 — 분산되지 않은 단일 진실 원천(Single Source of Truth)
 *   이벤트 분류는 {@link SecurityAuditEventType}으로 정규화
 *   한 번 기록되면 절대 수정/삭제 불가 (운영 환경에서 DB 트리거로 강제)
 *   인덱스 설계 — 시간/유형/사용자 기준 검색 최적화
 *
 * 왜 AdminAuditLog와 통합?
 * 두 로그가 별도면 보안 사고 분석 시 두 곳을 동시 조회해야 함.
 * 예: "사용자 1번이 어제 한 모든 활동" — 별도면 JOIN 불가능, 통합이면 단일 쿼리.
 *
 * 운영 시 주의
 *
 *   대용량 — 월 단위 파티셔닝 권장
 *   90일 이상은 별도 archive 테이블로 이전
 *   비동기 기록으로 본 트랜잭션 영향 차단
 *
 */
@Entity
@Table(name = "security_audit_log",
       indexes = {
           @Index(name = "idx_audit_event_time",     columnList = "eventType, occurredAt DESC"),
           @Index(name = "idx_audit_user_time",      columnList = "actorUserId, occurredAt DESC"),
           @Index(name = "idx_audit_severity_time",  columnList = "severity, occurredAt DESC"),
           @Index(name = "idx_audit_result_time",    columnList = "result, occurredAt DESC"),
           @Index(name = "idx_audit_ip_time",        columnList = "clientIp, occurredAt DESC"),
           @Index(name = "idx_audit_time_only",      columnList = "occurredAt DESC")
       })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecurityAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  이벤트 식별
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 이벤트 유형 — enum 이름 그대로 저장 (검색 용이) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SecurityAuditEventType eventType;

    /** 위협도 — enum 값 그대로 저장 (대시보드 필터링용) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SecurityAuditEventType.Severity severity;

    /** 카테고리 — enum 값 그대로 저장 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SecurityAuditEventType.Category category;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  주체 (Actor) — 누가 했나
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 행위자 사용자 ID — 비인증 요청은 null */
    @Column(name = "actor_user_id")
    private Long actorUserId;

    /** 행위자 이메일 — 추적용 (사용자 삭제 후에도 남아야 함) */
    @Column(length = 100)
    private String actorEmail;

    /** 행위자 역할 — BUYER/SELLER/ADMIN/SUPER_ADMIN */
    @Column(length = 20)
    private String actorRole;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  대상 (Target) — 무엇에 했나
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 대상 리소스 종류 — Product, User, Point 등 */
    @Column(length = 30)
    private String targetResource;

    /** 대상 리소스 ID — productId, userId 등 */
    @Column(length = 50)
    private String targetId;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  결과
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** SUCCESS / FAILURE */
    @Column(nullable = false, length = 10)
    private String result;

    /** 실패 시 에러 코드 — ErrorCode enum 이름 */
    @Column(length = 30)
    private String errorCode;

    /** 실패 시 에러 메시지 — 길이 제한 */
    @Column(length = 500)
    private String errorMessage;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  컨텍스트 — 어디서 어떻게
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 호출된 메서드 — 디버깅용 */
    @Column(length = 200)
    private String methodName;

    /** 메서드 인자 (민감 정보 마스킹된 상태) */
    @Column(length = 500)
    private String methodArgs;

    @Column(length = 45)
    private String clientIp;

    @Column(length = 255)
    private String userAgent;

    /** 요청 URI — Controller 단에서 캡처됐을 때만 */
    @Column(length = 255)
    private String requestUri;

    /** 요청 추적 ID — 분산 추적용 (선택) */
    @Column(length = 64)
    private String traceId;

    /** 추가 정보 — JSON 형태로 자유롭게 (디바이스 정보, 변경 내역 등) */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  타임스탬프
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 이벤트 발생 시각 — 명시적으로 받음 (생성 시각과 다를 수 있음) */
    @Column(nullable = false)
    private LocalDateTime occurredAt;

    /** 레코드 생성 시각 — 자동 */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Builder
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Builder
    private SecurityAuditLog(
            SecurityAuditEventType eventType,
            Long actorUserId, String actorEmail, String actorRole,
            String targetResource, String targetId,
            String result, String errorCode, String errorMessage,
            String methodName, String methodArgs,
            String clientIp, String userAgent, String requestUri, String traceId,
            String metadata, LocalDateTime occurredAt) {

        this.eventType = eventType;
        this.severity = eventType.getSeverity();
        this.category = eventType.getCategory();
        this.actorUserId = actorUserId;
        this.actorEmail = actorEmail;
        this.actorRole = actorRole;
        this.targetResource = targetResource;
        this.targetId = targetId;
        this.result = result;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.methodName = methodName;
        this.methodArgs = methodArgs;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.requestUri = requestUri;
        this.traceId = traceId;
        this.metadata = metadata;
        this.occurredAt = occurredAt != null ? occurredAt : LocalDateTime.now();
    }
}
