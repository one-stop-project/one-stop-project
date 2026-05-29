package com.sparta.one_stop.domain.point.entity;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.point.PointHistoryType;
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

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "point_history",
    indexes = {
        @Index(name = "idx_ph_point", columnList = "point_id, created_at"),
        @Index(name = "idx_ph_user", columnList = "user_id, created_at"),
        @Index(name = "idx_ph_order", columnList = "order_id"),
        @Index(name = "idx_ph_expire", columnList = "type, expire_at, remaining_amount"),
        @Index(name = "idx_ph_deduct", columnList = "point_id, expire_at, created_at, remaining_amount")
    }
)
public class PointHistory extends BaseEntity {

    // 포인트 이력 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    // 포인트 계정
    // 사용자별 포인트 잔액을 관리하는 Point와 연결된다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "point_id", nullable = false)
    private Point point;

    // 포인트 이력 소유자
    // point를 통해 user를 조회할 수 있지만, 마이페이지 포인트 이력 조회 편의를 위해 중복 저장한다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 관련 주문 정보
    // 주문 결제 사용, 주문 취소 복구, 배송 완료 적립 등 주문과 관련된 이력에서 사용한다
    // 테스트 충전, 만료 이력처럼 주문과 무관한 경우 null일 수 있다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    // 포인트 변동 금액
    // CHARGE / EARN / REFUND는 양수
    // USE / EXPIRE는 음수
    @Column(name = "amount", nullable = false)
    private Integer amount;

    // 차감 가능한 잔여 포인트
    // CHARGE / EARN / REFUND 이력에서만 사용한다
    // USE / EXPIRE 이력은 0으로 저장한다
    @Column(name = "remaining_amount", nullable = false)
    private Integer remainingAmount;

    // 포인트 이력 유형
    // CHARGE / EARN / USE / REFUND / EXPIRE
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PointHistoryType type;

    // 포인트 이력 설명
    // 예: 테스트용 포인트 충전, 배송 완료 적립, 주문 결제 사용, 주문 취소 복구, 포인트 만료
    @Column(name = "description", length = 255)
    private String description;

    // 포인트 만료일
    // CHARGE / EARN / REFUND 이력에서 사용한다
    // USE / EXPIRE 이력은 null이다
    @Column(name = "expire_at")
    private LocalDate expireAt;

    // == 생성자 ==
    private PointHistory(
        Point point,
        User user,
        Order order,
        Integer amount,
        Integer remainingAmount,
        PointHistoryType type,
        String description,
        LocalDate expireAt
    ) {
        validateRequiredFields(
            point,
            user,
            amount,
            remainingAmount,
            type
        );

        validateByType(
            type,
            amount,
            remainingAmount,
            expireAt
        );

        this.point = point;
        this.user = user;
        this.order = order;
        this.amount = amount;
        this.remainingAmount = remainingAmount;
        this.type = type;
        this.description = description;
        this.expireAt = expireAt;
    }

    // == 정적 생성 메서드 ==

    // 테스트용 포인트 충전 이력 생성
    // 충전 포인트는 차감 가능한 원본 포인트가 되므로 remainingAmount를 amount와 동일하게 저장한다
    public static PointHistory charge(
        Point point,
        Integer amount,
        String description,
        LocalDate expireAt
    ) {
        return new PointHistory(
            point,
            getPointUser(point),
            null,
            amount,
            amount,
            PointHistoryType.CHARGE,
            description,
            expireAt
        );
    }

    // 배송 완료 후 포인트 적립 이력 생성
    // 적립 포인트는 차감 가능한 원본 포인트가 되므로 remainingAmount를 amount와 동일하게 저장한다
    public static PointHistory earn(
        Point point,
        Order order,
        Integer amount,
        String description,
        LocalDate expireAt
    ) {
        return new PointHistory(
            point,
            getPointUser(point),
            order,
            amount,
            amount,
            PointHistoryType.EARN,
            description,
            expireAt
        );
    }

    // 주문 결제 시 포인트 사용 이력 생성
    // USE 이력은 사용 총액을 음수로 저장하고, remainingAmount는 0으로 저장한다
    public static PointHistory use(
        Point point,
        Order order,
        Integer amount,
        String description
    ) {
        validatePositiveAmount(amount);

        return new PointHistory(
            point,
            getPointUser(point),
            order,
            -amount,
            0,
            PointHistoryType.USE,
            description,
            null
        );
    }

    // 주문 취소 시 포인트 복구 이력 생성
    // 복구 포인트는 다시 차감 가능한 원본 포인트가 되므로 remainingAmount를 amount와 동일하게 저장한다
    // expireAt은 원래 차감되었던 포인트의 만료일을 따른다
    public static PointHistory refund(
        Point point,
        Order order,
        Integer amount,
        String description,
        LocalDate expireAt
    ) {
        return new PointHistory(
            point,
            getPointUser(point),
            order,
            amount,
            amount,
            PointHistoryType.REFUND,
            description,
            expireAt
        );
    }

    // 포인트 만료 이력 생성
    // EXPIRE 이력은 만료된 금액을 음수로 저장하고, remainingAmount는 0으로 저장한다
    public static PointHistory expire(
        Point point,
        Integer amount,
        String description
    ) {
        validatePositiveAmount(amount);

        return new PointHistory(
            point,
            getPointUser(point),
            null,
            -amount,
            0,
            PointHistoryType.EXPIRE,
            description,
            null
        );
    }

    // == 비즈니스 메서드 ==

    // 포인트 금액 양수 검증
    // 외부에서 양수 amount를 받아 내부적으로 음수 이력을 만들 때 사용한다
    private static void validatePositiveAmount(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("포인트 금액은 1 이상이어야 합니다.");
        }
    }

    // Point에서 User 조회
    // point null인 경우 point.getUser() NPE가 아니라 명확한 예외를 발생시키기 위해 사용한다
    private static User getPointUser(Point point) {
        if (point == null) {
            throw new IllegalArgumentException("포인트 계정은 필수입니다.");
        }

        return point.getUser();
    }

    // 원본 포인트 이력의 잔여 포인트 차감
    // 포인트 사용 시 FEFO + FIFO 기준으로 선택된 CHARGE / EARN / REFUND 이력에 대해 호출한다
    public void decreaseRemainingAmount(Integer amount) {
        validatePositiveAmount(amount);

        if (!isDeductibleSource()) {
            throw new IllegalStateException("차감 가능한 포인트 이력이 아닙니다.");
        }

        if (this.remainingAmount < amount) {
            throw new IllegalArgumentException("잔여 포인트가 부족합니다.");
        }

        this.remainingAmount -= amount;
    }

    // 잔여 포인트 만료 처리
    // 만료 스케줄러 또는 배치에서 호출하며, 만료된 금액을 반환한다
    public Integer expireRemainingAmount() {
        if (!isDeductibleSource()) {
            throw new IllegalStateException("만료 가능한 포인트 이력이 아닙니다.");
        }

        Integer expiredAmount = this.remainingAmount;
        this.remainingAmount = 0;

        return expiredAmount;
    }

    // == 검증 메서드 ==

    // 차감 가능한 원본 포인트 이력 여부
    // CHARGE / EARN / REFUND 이력만 remainingAmount를 가지고 차감 대상이 될 수 있다
    public boolean isDeductibleSource() {
        return this.type == PointHistoryType.CHARGE
            || this.type == PointHistoryType.EARN
            || this.type == PointHistoryType.REFUND;
    }

    // 공통 필수값 검증
    private void validateRequiredFields(
        Point point,
        User user,
        Integer amount,
        Integer remainingAmount,
        PointHistoryType type
    ) {
        if (point == null) {
            throw new IllegalArgumentException("포인트 계정은 필수입니다.");
        }

        if (user == null) {
            throw new IllegalArgumentException("포인트 사용자는 필수입니다.");
        }

        if (amount == null || amount == 0) {
            throw new IllegalArgumentException("포인트 변동 금액은 0일 수 없습니다.");
        }

        if (remainingAmount == null || remainingAmount < 0) {
            throw new IllegalArgumentException("잔여 포인트는 0 이상이어야 합니다.");
        }

        if (type == null) {
            throw new IllegalArgumentException("포인트 이력 유형은 필수입니다.");
        }
    }

    // 포인트 이력 유형별 정책 검증
    private void validateByType(
        PointHistoryType type,
        Integer amount,
        Integer remainingAmount,
        LocalDate expireAt
    ) {
        // CHARGE / EARN / REFUND는 사용 가능한 포인트를 증가시키는 이력이다
        // amount는 양수, remainingAmount는 amount와 동일해야 하며, 만료일이 필수이다
        if (type == PointHistoryType.CHARGE
            || type == PointHistoryType.EARN
            || type == PointHistoryType.REFUND) {
            if (amount <= 0) {
                throw new IllegalArgumentException("적립/충전/복구 금액은 양수여야 합니다.");
            }

            if (!amount.equals(remainingAmount)) {
                throw new IllegalArgumentException("생성 시 잔여 포인트는 변동 금액과 같아야 합니다.");
            }

            if (expireAt == null) {
                throw new IllegalArgumentException("포인트 만료일은 필수입니다.");
            }

            return;
        }

        // USE / EXPIRE는 포인트를 감소시키는 이력이다
        // amount는 음수, remainingAmount는 0이어야 한다
        if (type == PointHistoryType.USE || type == PointHistoryType.EXPIRE) {
            if (amount >= 0) {
                throw new IllegalArgumentException("사용/만료 금액은 음수여야 합니다.");
            }

            if (remainingAmount != 0) {
                throw new IllegalArgumentException("사용/만료 이력의 잔여 포인트는 0이어야 합니다.");
            }
        }
    }

}
