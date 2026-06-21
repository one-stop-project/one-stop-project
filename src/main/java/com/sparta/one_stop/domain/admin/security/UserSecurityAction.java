package com.sparta.one_stop.domain.admin.security;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name="user_security_actions",indexes={@Index(name="idx_usa_target_active",columnList="target_user_id,action_type,active")})
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class UserSecurityAction {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="target_user_id",nullable=false) private Long targetUserId;
 @Column(name="admin_user_id",nullable=false) private Long adminUserId;
 @Enumerated(EnumType.STRING) @Column(name="action_type",nullable=false,length=50) private SecurityActionType actionType;
 @Column(name="reason_code",nullable=false,length=100) private String reasonCode;
 @Column(name="reason_detail",length=1000) private String reasonDetail;
 @Column(name="started_at",nullable=false) private LocalDateTime startedAt;
 @Column(name="expires_at") private LocalDateTime expiresAt;
 @Column(nullable=false) private boolean active;
 public static UserSecurityAction create(Long target,Long admin,SecurityActionType type,String code,String detail,LocalDateTime expires){
  UserSecurityAction a=new UserSecurityAction();a.targetUserId=target;a.adminUserId=admin;a.actionType=type;a.reasonCode=code;
  a.reasonDetail=detail;a.startedAt=LocalDateTime.now();a.expiresAt=expires;a.active=type==SecurityActionType.SUSPEND;return a;
 }
 public boolean isExpired(LocalDateTime now){return active&&expiresAt!=null&&!expiresAt.isAfter(now);}
 public void deactivate(){active=false;}
}
