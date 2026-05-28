package com.sparta.one_stop.domain.order.entity;

import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
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
    name = "order_items",
    indexes = {
        @Index(name = "idx_oi_order", columnList = "order_id"),
        @Index(name = "idx_oi_seller", columnList = "seller_id, status"),
        @Index(name = "idx_oi_status", columnList = "status, created_at")
    }
)
public class OrderItem extends BaseEntity {

    // 주문 상품 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long id;

    // 소속 주문 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // 상품 옵션 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private ProductItem productItem;

    // 판매자 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    // 주문 시점 상품명 스냅샷
    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    // 구매 수량
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    // 주문 시점 가격 스냅샷
    @Column(name = "price", nullable = false)
    private Long price;

    // 주문 시점 썸네일 URL 스냅샷
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    // 주문 상품 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderItemStatus status;

    // == 생성자 ==
    public OrderItem(
        Order order,
        ProductItem productItem,
        Seller seller,
        String itemName,
        Integer quantity,
        Long price,
        String thumbnailUrl
    ) {

        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다.");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }

        if (price < 0) {
            throw new IllegalArgumentException("가격은 0원 이상이어야 합니다.");
        }

        this.order = order;
        this.productItem = productItem;
        this.seller = seller;
        this.itemName = itemName;
        this.quantity = quantity;
        this.price = price;
        this.thumbnailUrl = thumbnailUrl;
        this.status = OrderItemStatus.PENDING_PAYMENT;
    }

    // == 비즈니스 메서드 ==

    // 결제 완료 후 주문 접수 처리
    public void markOrdered() {

        if (this.status != OrderItemStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("주문 접수는 PENDING_PAYMENT 상태에서만 처리할 수 있습니다.");
        }

        this.status = OrderItemStatus.ORDERED;
    }

    // 주문 확정
    public void confirm() {

        if (this.status != OrderItemStatus.ORDERED) {
            throw new IllegalStateException("주문 확정은 ORDERED 상태에서만 가능합니다.");
        }

        this.status = OrderItemStatus.CONFIRMED;
    }

    // 배송 시작
    public void startShipping() {

        if (this.status != OrderItemStatus.CONFIRMED) {
            throw new IllegalStateException("배송 시작은 CONFIRMED 상태에서만 가능합니다.");
        }

        this.status = OrderItemStatus.SHIPPING;
    }

    // 배송 완료
    public void completeDelivery() {

        if (this.status != OrderItemStatus.SHIPPING) {
            throw new IllegalStateException("배송 완료는 SHIPPING 상태에서만 가능합니다.");
        }

        this.status = OrderItemStatus.DELIVERED;
    }

    // 주문 거절
    public void reject() {

        if (this.status != OrderItemStatus.ORDERED) {
            throw new IllegalStateException("주문 거절은 ORDERED 상태에서만 가능합니다.");
        }

        this.status = OrderItemStatus.REJECTED;
    }

    // 주문 취소
    public void cancel() {
        if (this.status != OrderItemStatus.PENDING_PAYMENT
            && this.status != OrderItemStatus.ORDERED
            && this.status != OrderItemStatus.CONFIRMED)  {
            throw new IllegalStateException("현재 상태에서는 주문 상품을 취소할 수 없습니다.");
        }

        this.status = OrderItemStatus.CANCELLED;
    }

}
