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
 *
 * <p><b>시스템 이벤트 처리</b>
 * <p>스케줄러·배치 같은 비-인증 컨텍스트에서도 감사 로그가 필요한 경우:
 * <ul>
 *   <li>{@code adminId} = {@link #SYSTEM_ACTOR_ID} (0L)</li>
 *   <li>{@code adminUsername} = {@link #SYSTEM_ACTOR_USERNAME} ("SYSTEM")</li>
 * </ul>
 * <br>이렇게 하면 NOT NULL 제약 위반 없이도 시스템 이벤트를 명시 기록 가능.
 */
@Entity
@Table(name = "admin_audit_log",
    indexes = {
        @Index(name = "idx_audit_action_time", columnList = "action, occurredAt DESC"),
        @Index(name = "idx_audit_resource_time", columnList = "targetResource, occurredAt DESC"),
        @Index(name = "idx_audit_result_time", columnList = "result, occurredAt DESC"),
        @Index(name = "idx_audit_admin_time", columnList = "adminId, occurredAt DESC")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {

    /** 시스템 이벤트 식별자 — 스케줄러/배치 등 비-인증 컨텍스트 */
    public static final Long SYSTEM_ACTOR_ID = 0L;
    public static final String SYSTEM_ACTOR_USERNAME = "SYSTEM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(nullable = false, length = 30)
    private String targetResource;

    @Column(nullable = false)
    private Long adminId;

    @Column(nullable = false, length = 50)
    private String adminUsername;

    @Column(length = 200)
    private String methodName;

    @Column(length = 500)
    private String args;

    @Column(nullable = false, length = 10)
    private String result;

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
                          Long adminId, String adminUsername,
                          String clientIp, String userAgent, LocalDateTime occurredAt) {
        // ── 기존 fail-fast 검증 ──
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action은 필수입니다.");
        }
        if (targetResource == null || targetResource.isBlank()) {
            throw new IllegalArgumentException("targetResource는 필수입니다.");
        }
        if (result == null || result.isBlank()) {
            throw new IllegalArgumentException("result는 필수입니다.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt은 필수입니다.");
        }
        // ★ [수정] DB NOT NULL 컬럼은 모두 fail-fast로 검증해야 함
        //   - 빌더에서 안 막으면 INSERT 시점에 실패 → AOP에서 try-catch로 삼킴 → 감사 로그 유실
        //   - 시스템 이벤트는 SYSTEM_ACTOR_ID/USERNAME 사용 권장
        if (adminId == null) {
            throw new IllegalArgumentException(
                "adminId는 필수입니다. 시스템 이벤트는 SYSTEM_ACTOR_ID(0L)를 사용하세요.");
        }
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException(
                "adminUsername은 필수입니다. 시스템 이벤트는 SYSTEM_ACTOR_USERNAME(\"SYSTEM\")을 사용하세요.");
        }

        this.action = action;
        this.targetResource = targetResource;
        this.methodName = methodName;
        this.args = args;
        this.result = result;
        this.errorDetail = errorDetail;
        this.adminId = adminId;
        this.adminUsername = adminUsername;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.occurredAt = occurredAt;
    }
}
