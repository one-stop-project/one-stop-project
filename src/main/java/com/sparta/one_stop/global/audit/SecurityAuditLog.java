package com.sparta.one_stop.global.audit;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="security_audit_logs", indexes={
    @Index(name="idx_sal_event_time",columnList="event_type,occurred_at"),
    @Index(name="idx_sal_actor_time",columnList="actor_user_id,occurred_at"),
    @Index(name="idx_sal_ip_hash_time",columnList="client_ip_hash,occurred_at"),
    @Index(name="idx_sal_suspicious_time",columnList="suspicious,occurred_at")})
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SecurityAuditLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(name="event_type",nullable=false,length=60) private SecurityAuditEventType eventType;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private SecuritySeverity severity;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private SecurityAuditEventType.Category category;
    @Column(name="actor_user_id") private Long actorUserId;
    @Column(name="actor_role",length=20) private String actorRole;
    @Column(name="target_user_id") private Long targetUserId;
    @Column(name="target_resource",length=30) private String targetResource;
    @Column(name="target_id",length=50) private String targetId;
    @Column(nullable=false,length=20) private String result;
    @Column(name="error_code",length=100) private String errorCode;
    @Column(name="detail_message",length=1000) private String detailMessage;
    @Lob @Column(name="client_ip_encrypted") private String clientIpEncrypted;
    @Column(name="client_ip_hash",length=128) private String clientIpHash;
    @Column(name="client_ip_prefix",length=64) private String clientIpPrefix;
    @Column(name="user_agent_hash",length=128) private String userAgentHash;
    @Column(name="device_id_hash",length=128) private String deviceIdHash;
    @Column(name="request_id",length=100) private String requestId;
    @Column(name="rule_code",length=100) private String ruleCode;
    @Column(name="request_path",length=200) private String requestPath;
    @Column(nullable=false) private boolean suspicious;
    @Column(name="occurred_at",nullable=false) private LocalDateTime occurredAt;
    @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;

    public static SecurityAuditLog from(PreparedSecurityAuditEvent e) {
        SecurityAuditLog l=new SecurityAuditLog();
        l.eventType=e.eventType(); l.severity=e.severity(); l.category=e.category(); l.actorUserId=e.actorUserId();
        l.actorRole=e.actorRole(); l.targetUserId=e.targetUserId(); l.targetResource=e.targetResource(); l.targetId=e.targetId();
        l.result=e.result(); l.errorCode=e.errorCode(); l.detailMessage=e.detailMessage();
        l.clientIpEncrypted=e.clientIpEncrypted(); l.clientIpHash=e.clientIpHash(); l.clientIpPrefix=e.clientIpPrefix();
        l.userAgentHash=e.userAgentHash(); l.deviceIdHash=e.deviceIdHash(); l.requestId=e.requestId(); l.ruleCode=e.ruleCode();
        l.requestPath=e.requestPath(); l.suspicious=e.suspicious(); l.occurredAt=e.occurredAt(); return l;
    }
}
