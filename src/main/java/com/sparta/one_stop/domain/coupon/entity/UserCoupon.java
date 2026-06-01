package com.sparta.one_stop.domain.coupon.entity;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "user_coupon",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_user_coupon_user_coupon",
            columnNames = {"user_id", "coupon_id"}
        ),
        @UniqueConstraint(
            name = "uk_uc_used_order",
            columnNames = {"used_order_id"}
        )
    },
    indexes = {
        @Index(name = "idx_uc_user", columnList = "user_id, status"),
        @Index(name = "idx_uc_coupon", columnList = "coupon_id")
    }
)
public class UserCoupon extends BaseEntity {

    // 사용자 쿠폰 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_coupon_id")
    private Long id;

    // 쿠폰 소유자
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 발급받은 쿠폰 마스터 정보
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    // 쿠폰이 사용된 주문
    // 결제 승인 성공 시 저장된다
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_order_id")
    private Order usedOrder;

    // 사용자 쿠폰 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserCouponStatus status;

    // 실제 사용 일시
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    // == 생성자 ==
    public UserCoupon(
        User user,
        Coupon coupon
    ) {
        if (user == null) {
            throw new IllegalArgumentException("쿠폰 소유자는 필수입니다.");
        }

        if (coupon == null) {
            throw new IllegalArgumentException("쿠폰 정보는 필수입니다.");
        }

        this.user = user;
        this.coupon = coupon;
        this.status = UserCouponStatus.AVAILABLE;
    }

    // == 비즈니스 메서드 ==

    // 쿠폰 사용 처리
    // 결제 승인 성공 시 호출한다
    public void use(
        Order order,
        LocalDateTime usedAt
    ) {
        if (order == null) {
            throw new IllegalArgumentException("쿠폰을 사용할 주문 정보는 필수입니다.");
        }

        if (usedAt == null) {
            throw new IllegalArgumentException("쿠폰 사용 일시는 필수입니다.");
        }

        if (this.status != UserCouponStatus.AVAILABLE) {
            throw new IllegalStateException("사용 가능한 쿠폰이 아닙니다.");
        }

        if (this.coupon.isExpired(usedAt)) {
            throw new IllegalStateException("만료된 쿠폰은 사용할 수 없습니다.");
        }

        this.status = UserCouponStatus.USED;
        this.usedOrder = order;
        this.usedAt = usedAt;
    }

    // 쿠폰 복구
    // 주문 취소 시 호출한다
    public void restore(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다.");
        }

        if (this.status != UserCouponStatus.USED) {
            throw new IllegalStateException("사용 완료된 쿠폰만 복구할 수 있습니다.");
        }

        this.usedOrder = null;
        this.usedAt = null;

        if (this.coupon.isExpired(now)) {
            this.status = UserCouponStatus.EXPIRED;
            return;
        }

        this.status = UserCouponStatus.AVAILABLE;
    }

    // 쿠폰 만료 처리
    public void expire(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다.");
        }

        if (this.status == UserCouponStatus.EXPIRED) {
            return;
        }

        if (this.status == UserCouponStatus.USED) {
            throw new IllegalStateException("이미 사용된 쿠폰은 만료 처리할 수 없습니다.");
        }

        if (!this.coupon.isExpired(now)) {
            throw new IllegalStateException("아직 만료되지 않은 쿠폰입니다.");
        }

        this.status = UserCouponStatus.EXPIRED;
    }

    // == 검증 메서드 ==

    // 쿠폰 사용 가능 여부 검증
    public void validateUsable(
        Long userId,
        LocalDateTime now
    ) {
        validateOwner(userId);

        if (this.status != UserCouponStatus.AVAILABLE) {
            throw new IllegalStateException("사용 가능한 쿠폰이 아닙니다.");
        }

        if (!this.coupon.isInPeriod(now)) {
            throw new IllegalStateException("쿠폰 사용 가능 기간이 아닙니다.");
        }
    }

    // 쿠폰 소유자 검증
    private void validateOwner(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }

        if (!this.user.getId().equals(userId)) {
            throw new IllegalStateException("본인 쿠폰만 사용할 수 있습니다.");
        }
    }

}
