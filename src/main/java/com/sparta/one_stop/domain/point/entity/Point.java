package com.sparta.one_stop.domain.point.entity;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "points")
public class Point extends BaseEntity {

    // 포인트 계정 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_id")
    private Long id;

    // 포인트 소유자
    // BUYER 기준 1명의 사용자는 1개의 포인트 계정을 가진다
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // 현재 사용 가능한 포인트 총액
    // 빠른 조회를 위한 요약 값이며, PointHistory의 사용 가능 remainingAmount 합계와 일치해야 한다
    @Column(name = "balance", nullable = false)
    private Integer balance;

    // 포인트 차감 동시성 제어용 낙관적 락 버전
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    // == 생성자 ==
    public Point(User user) {
        if (user == null) {
            throw new IllegalArgumentException("포인트 소유자는 필수입니다.");
        }

        this.user = user;
        this.balance = 0;
        this.version = 0;
    }

    // == 비즈니스 메서드 ==

    // 포인트 증가
    // 충전, 적립, 복구 시 사용
    public void increaseBalance(Integer amount) {
        validatePositiveAmount(amount);

        this.balance += amount;
    }

    // 포인트 감소
    // 결제 사용, 만료 처리 시 사용
    public void decreaseBalance(Integer amount) {
        validatePositiveAmount(amount);

        if (this.balance < amount) {
            throw new IllegalArgumentException("보유 포인트가 부족합니다.");
        }

        this.balance -= amount;
    }

    // 포인트 금액 검증
    private void validatePositiveAmount(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("포인트 금액은 1 이상이어야 합니다.");
        }
    }

}
