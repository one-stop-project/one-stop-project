package com.sparta.one_stop.domain.subscription.entity;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "subscription",
    indexes = {
        @Index(
            name = "idx_sub_user",
            columnList = "user_id,status,created_at"
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "next_payment_date", nullable = false)
    private LocalDateTime nextPaymentDate;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Builder
    public Subscription(
        User user,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime nextPaymentDate
    ) {
        this.user = user;
        this.startAt = startAt;
        this.endAt = endAt;
        this.nextPaymentDate = nextPaymentDate;
        this.status = SubscriptionStatus.ACTIVE;
    }

    /**
     * 구독 해지
     */
    public void cancel(String reason) {

        if (this.status == SubscriptionStatus.CANCELLED) {
            throw new CustomException(ErrorCode.SUBSCRIPTION_003);
        }

        if (this.status == SubscriptionStatus.EXPIRED) {
            throw new CustomException(ErrorCode.SUBSCRIPTION_011);
        }

        this.cancelReason = reason;
        this.status = SubscriptionStatus.CANCELLED;
    }

    /**
     * 자동 결제 성공
     */
    public void renew() {

        if (this.status != SubscriptionStatus.ACTIVE) {
            throw new CustomException(ErrorCode.SUBSCRIPTION_004);
        }

        this.endAt = this.endAt.plusDays(30);
        this.nextPaymentDate = this.nextPaymentDate.plusDays(30);
    }

    /**
     * 자동 결제 실패
     */
    public void expire() {
        if (this.status == SubscriptionStatus.EXPIRED) {
            return;
        }
        this.status = SubscriptionStatus.EXPIRED;
    }

    /**
     * 유효 구독 여부
     */
    public boolean isValidSubscription() {
        return this.status == SubscriptionStatus.ACTIVE
            || this.status == SubscriptionStatus.CANCELLED;
    }
}
