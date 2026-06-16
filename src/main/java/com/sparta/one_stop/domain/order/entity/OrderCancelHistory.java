package com.sparta.one_stop.domain.order.entity;

import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.order.CancelActorType;
import com.sparta.one_stop.global.enums.order.OrderCancelType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_cancel_history")
public class OrderCancelHistory extends BaseEntity {

    // 취소/거절 이력 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cancel_history_id")
    private Long id;

    // 취소/거절 대상 주문
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // 주문 상품 단위 거절/취소인 경우 사용
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    // 처리 주체 유형
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 30)
    private CancelActorType actorType;

    // 실제 처리자 ID, 처리 주체 유형에 따라 필수 여부 결정
    @Column(name = "actor_id")
    private Long actorId;

    // 취소/거절 유형
    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_type", nullable = false, length = 30)
    private OrderCancelType cancelType;

    // 취소/거절 사유
    @Column(name = "reason", length = 255)
    private String reason;

    // 이 이력 건에서 취소/거절 처리된 금액
    @Column(name = "cancelled_price", nullable = false)
    private Long cancelledPrice;

    // 이 이력 건으로 복구된 포인트, 복구 없음은 0
    @Column(name = "restored_point", nullable = false)
    private Integer restoredPoint;

    // == 생성자 ==
    public OrderCancelHistory(
        Order order,
        OrderItem orderItem,
        CancelActorType actorType,
        Long actorId,
        OrderCancelType cancelType,
        String reason,
        Long cancelledPrice,
        Integer restoredPoint
    ) {
        validateConstructorArguments(
            order,
            actorType,
            actorId,
            cancelType,
            cancelledPrice,
            restoredPoint
        );

        this.order = order;
        this.orderItem = orderItem;
        this.actorType = actorType;
        this.actorId = actorId;
        this.cancelType = cancelType;
        this.reason = reason;
        this.cancelledPrice = cancelledPrice;
        this.restoredPoint = restoredPoint;
    }

    // == 검증 메서드 ==

    private void validateConstructorArguments(
        Order order,
        CancelActorType actorType,
        Long actorId,
        OrderCancelType cancelType,
        Long cancelledPrice,
        Integer restoredPoint
    ) {
        if (order == null) {
            throw new CustomException(ErrorCode.ORDER_020);
        }

        if (actorType == null) {
            throw new CustomException(ErrorCode.ORDER_032);
        }

        if (cancelType == null) {
            throw new CustomException(ErrorCode.ORDER_033);
        }

        validateActorId(actorType, actorId);

        if (cancelledPrice == null || cancelledPrice < 0) {
            throw new CustomException(ErrorCode.ORDER_037);
        }

        if (restoredPoint == null || restoredPoint < 0) {
            throw new CustomException(ErrorCode.ORDER_038);
        }
    }

    // 처리 주체 유형에 따른 actorId 필수 여부 검증
    private void validateActorId(
        CancelActorType actorType,
        Long actorId
    ) {
        if (actorType.isActorIdRequired() && actorId == null) {
            throw new CustomException(ErrorCode.ORDER_034);
        }

        if (!actorType.isActorIdRequired() && actorId != null) {
            throw new CustomException(ErrorCode.ORDER_035);
        }

        if (actorId != null && actorId <= 0) {
            throw new CustomException(ErrorCode.ORDER_036);
        }
    }

}
