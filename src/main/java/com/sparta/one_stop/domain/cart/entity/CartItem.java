package com.sparta.one_stop.domain.cart.entity;

import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "cart_items",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_cart_item",
            columnNames = {"cart_id", "item_id"}
        )
    }
)
public class CartItem extends BaseEntity {

    // 카트 아이템 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_id")
    private Long id;

    // 장바구니 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    // 상품 옵션 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private ProductItem productItem;

    // 수량
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    // == 생성자 ==
    public CartItem(Cart cart, ProductItem productItem, int quantity) {

        if (cart == null) {
            throw new IllegalArgumentException("장바구니 정보는 필수입니다.");
        }

        if (productItem == null) {
            throw new IllegalArgumentException("상품 옵션 정보는 필수입니다.");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }

        if (quantity > 99) {
            throw new IllegalArgumentException("장바구니 최대 수량은 99개입니다.");
        }

        this.cart = cart;
        this.productItem = productItem;
        this.quantity = quantity;
    }


    // == 비즈니스 메서드 ==
    // 수량 증가
    public void increaseQuantity(int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }

        if (this.quantity + quantity > 99) {
            throw new IllegalArgumentException("장바구니 최대 수량은 99개입니다.");
        }

        this.quantity += quantity;
    }

    // 수량 감소
    public void decreaseQuantity(int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }

        if (quantity >= this.quantity) {
            throw new IllegalArgumentException("수량은 1 미만이 될 수 없습니다.");
        }

        this.quantity -= quantity;
    }

    // 장바구니 상품의 최종 수량 변경
    // Redis 비로그인 장바구니 merge처럼 최종 수량을 계산한 뒤 반영할 때 사용
    public void changeQuantity(int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }

        if (quantity > 99) {
            throw new IllegalArgumentException("장바구니 최대 수량은 99개입니다.");
        }

        this.quantity = quantity;
    }

}
