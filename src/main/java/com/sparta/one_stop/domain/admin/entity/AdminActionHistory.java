package com.sparta.one_stop.domain.admin.entity;

import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.admin.AdminActionTarget;
import com.sparta.one_stop.global.enums.admin.AdminActionType;
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

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "admin_action_history",
    indexes = {
        // 관리자별 처리 이력 조회용
        @Index(name = "idx_aah_actor", columnList = "actor_id, created_at"),
        // 대상별 처리 이력 조회용
        @Index(name = "idx_aah_target", columnList = "target_type, target_id, created_at")
    }
)
public class AdminActionHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    // 처리한 관리자 ID
    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    // 처리 대상 타입 (SELLER / PRODUCT)
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private AdminActionTarget targetType;

    // 처리 대상 ID (sellerId 또는 productId)
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    // 처리 액션 (APPROVE / REJECT / FORCE_INACTIVE)
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private AdminActionType action;

    // 처리 사유 (반려/강제비활성화 시 필수, 승인 시 null)
    @Column(name = "reason", length = 255)
    private String reason;

    @Builder
    private AdminActionHistory(
        Long actorId,
        AdminActionTarget targetType,
        Long targetId,
        AdminActionType action,
        String reason
    ) {
        this.actorId = actorId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.action = action;
        this.reason = reason;
    }
}
