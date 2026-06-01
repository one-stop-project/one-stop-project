package com.sparta.one_stop.domain.coupon.entity;

import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.coupon.CouponDiscountType;
import com.sparta.one_stop.global.enums.coupon.CouponStatus;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "coupon",
    indexes = {
        @Index(name = "idx_coupon_status_period", columnList = "status, start_at, expired_at"),
        @Index(name = "idx_coupon_created_at", columnList = "created_at")
    }
)
public class Coupon extends BaseEntity {

    // 쿠폰 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long id;

    // 쿠폰명
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // 할인 타입
    // RATE: 정률 할인, FIXED: 정액 할인
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private CouponDiscountType discountType;

    // 할인 값
    // RATE: 할인율(%), FIXED: 할인 금액
    @Column(name = "discount_value", nullable = false)
    private Integer discountValue;

    // 최소 주문 금액 조건
    @Column(name = "min_order_price", nullable = false)
    private Long minOrderPrice;

    // 정률 쿠폰 최대 할인 한도
    // RATE 타입 필수, FIXED 타입 미사용
    @Column(name = "max_discount_price")
    private Long maxDiscountPrice;

    // 발급 가능 총 수량
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    // DB 기준 실제 발급 완료 수량
    @Column(name = "issued_quantity", nullable = false)
    private Integer issuedQuantity;

    // 쿠폰 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponStatus status;

    // 발급/사용 시작일
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    // 발급/사용 만료일
    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    // == 생성자 ==
    public Coupon(
        String name,
        CouponDiscountType discountType,
        Integer discountValue,
        Long minOrderPrice,
        Long maxDiscountPrice,
        Integer totalQuantity,
        LocalDateTime startAt,
        LocalDateTime expiredAt
    ) {
        validate(
            name,
            discountType,
            discountValue,
            minOrderPrice,
            maxDiscountPrice,
            totalQuantity,
            startAt,
            expiredAt
        );

        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderPrice = minOrderPrice;
        this.maxDiscountPrice = maxDiscountPrice;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0;
        this.status = CouponStatus.ACTIVE;
        this.startAt = startAt;
        this.expiredAt = expiredAt;
    }

    // == 비즈니스 메서드 ==

    // 발급 수량 증가
    // 단, 실제 선착순 발급에서는 Repository 원자 UPDATE 사용 권장
    public void increaseIssuedQuantity() {
        if (this.issuedQuantity >= this.totalQuantity) {
            throw new IllegalStateException("쿠폰 발급 수량이 모두 소진되었습니다.");
        }

        this.issuedQuantity++;
    }

    // 쿠폰 할인 금액 계산
    public Long calculateDiscountPrice(Long orderPrice) {
        validateOrderPrice(orderPrice);

        if (orderPrice < this.minOrderPrice) {
            throw new IllegalStateException("쿠폰 최소 주문 금액을 충족하지 못했습니다.");
        }

        if (this.discountType == CouponDiscountType.FIXED) {
            return Math.min(
                this.discountValue.longValue(),
                orderPrice
            );
        }

        long discountPrice = orderPrice * this.discountValue / 100;

        return Math.min(
            discountPrice,
            this.maxDiscountPrice
        );
    }

    // 쿠폰 비활성화
    public void deactivate() {
        if (this.status == CouponStatus.INACTIVE) {
            throw new IllegalStateException("이미 비활성화된 쿠폰입니다.");
        }

        this.status = CouponStatus.INACTIVE;
    }

    // == 검증 메서드 ==

    // 발급 가능 여부 검증
    public void validateIssuable(LocalDateTime now) {
        if (this.status != CouponStatus.ACTIVE) {
            throw new IllegalStateException("비활성 쿠폰은 발급할 수 없습니다.");
        }

        if (!isInPeriod(now)) {
            throw new IllegalStateException("쿠폰 발급 가능 기간이 아닙니다.");
        }

        if (this.issuedQuantity >= this.totalQuantity) {
            throw new IllegalStateException("쿠폰 발급 수량이 모두 소진되었습니다.");
        }
    }

    private void validateOrderPrice(Long orderPrice) {
        if (orderPrice == null || orderPrice < 0) {
            throw new IllegalArgumentException("주문 금액은 0원 이상이어야 합니다.");
        }
    }

    // == 조회 메서드 ==

    // 사용 가능 기간 여부
    public boolean isInPeriod(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다.");
        }

        return !now.isBefore(this.startAt)
            && !now.isAfter(this.expiredAt);
    }

    // 쿠폰 만료 여부
    public boolean isExpired(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다.");
        }

        return now.isAfter(this.expiredAt);
    }

    // == 생성 검증 메서드 ==

    private void validate(
        String name,
        CouponDiscountType discountType,
        Integer discountValue,
        Long minOrderPrice,
        Long maxDiscountPrice,
        Integer totalQuantity,
        LocalDateTime startAt,
        LocalDateTime expiredAt
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("쿠폰명은 필수입니다.");
        }

        if (discountType == null) {
            throw new IllegalArgumentException("쿠폰 할인 타입은 필수입니다.");
        }

        if (discountValue == null || discountValue <= 0) {
            throw new IllegalArgumentException("쿠폰 할인 값은 1 이상이어야 합니다.");
        }

        if (minOrderPrice == null || minOrderPrice < 0) {
            throw new IllegalArgumentException("최소 주문 금액은 0원 이상이어야 합니다.");
        }

        if (totalQuantity == null || totalQuantity <= 0) {
            throw new IllegalArgumentException("쿠폰 발급 가능 총 수량은 1 이상이어야 합니다.");
        }

        if (startAt == null) {
            throw new IllegalArgumentException("쿠폰 시작일은 필수입니다.");
        }

        if (expiredAt == null) {
            throw new IllegalArgumentException("쿠폰 만료일은 필수입니다.");
        }

        if (!expiredAt.isAfter(startAt)) {
            throw new IllegalArgumentException("쿠폰 만료일은 시작일 이후여야 합니다.");
        }

        validateDiscountPolicy(
            discountType,
            discountValue,
            maxDiscountPrice
        );
    }

    private void validateDiscountPolicy(
        CouponDiscountType discountType,
        Integer discountValue,
        Long maxDiscountPrice
    ) {
        if (discountType == CouponDiscountType.RATE) {
            if (discountValue > 100) {
                throw new IllegalArgumentException("정률 쿠폰 할인율은 100 이하이어야 합니다.");
            }

            if (maxDiscountPrice == null || maxDiscountPrice <= 0) {
                throw new IllegalArgumentException("정률 쿠폰은 최대 할인 금액이 필수입니다.");
            }
        }

        if (discountType == CouponDiscountType.FIXED) {
            if (maxDiscountPrice != null) {
                throw new IllegalArgumentException("정액 쿠폰은 최대 할인 금액을 사용할 수 없습니다.");
            }
        }
    }

}
