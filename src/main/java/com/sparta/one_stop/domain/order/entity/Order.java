package com.sparta.one_stop.domain.order.entity;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.enums.order.OrderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_orders_user", columnList = "user_id, created_at"),
        @Index(name = "idx_orders_status", columnList = "status, created_at")
    }
)
public class Order extends BaseEntity {

    // 주문 id
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    // 주문자 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // MVP 단계에서 쿠폰과 구독은 구현 안함(추후 구현 예정)
//    // 구독 정보
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "subscription_id")
//    private Subscription subscription;
//
//    // 적용 쿠폰 정보
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "user_coupon_id")
//    private UserCoupon userCoupon;

    // 총 주문 금액
    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    // 총 할인 금액
    @Column(name = "discount_price", nullable = false)
    private Long discountPrice;

    // 최종 결제 금액
    @Column(name = "final_price", nullable = false)
    private Long finalPrice;

    // 사용 포인트
    @Column(name = "used_point", nullable = false)
    private Integer usedPoint;

    // 주문 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    // 수령인 이름
    @Column(name = "receiver_name", nullable = false, length = 50)
    private String receiverName;

    // 수령인 연락처
    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    // 배송 주소
    @Column(name = "receiver_address", nullable = false, length = 255)
    private String receiverAddress;

    // 배송 요청사항
    @Column(name = "delivery_message", length = 50)
    private String deliveryMessage;

    // 배송비
    @Column(name = "delivery_fee", nullable = false)
    private Long deliveryFee;

    // 주문 유입 경로
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    // == 생성자 ==
    public Order(
        User user,
        Long totalPrice,
        Long discountPrice,
        Long finalPrice,
        Integer usedPoint,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        String deliveryMessage,
        Long deliveryFee,
        OrderType orderType
    ) {
        this.user = user;
        this.totalPrice = totalPrice;
        this.discountPrice = discountPrice;
        this.finalPrice = finalPrice;
        this.usedPoint = usedPoint;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.receiverAddress = receiverAddress;
        this.deliveryMessage = deliveryMessage;
        this.deliveryFee = deliveryFee;
        this.orderType = orderType;
        this.status = OrderStatus.PENDING_PAYMENT;
    }

    // == 비즈니스 메서드 ==

    // 결제 완료
    public void completePayment() {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("결제 대기 상태에서만 결제 완료 처리할 수 있습니다.");
        }

        this.status = OrderStatus.PAID;
    }

    // 주문 취소
    public void cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 주문입니다.");
        }

        this.status = OrderStatus.CANCELLED;
    }

}
