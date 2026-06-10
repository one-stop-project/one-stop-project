package com.sparta.one_stop.global.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
