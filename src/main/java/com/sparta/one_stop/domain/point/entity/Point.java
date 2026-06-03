package com.sparta.one_stop.domain.point.entity;

import com.sparta.one_stop.domain.point.util.PointIntegrityHasher;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
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

    // 무결성해시(HMAC-SHA256)
    @Column(name = "integrity_hash", nullable = false, length = 64)
    private String integrityHash;

    // == 생성자 ==
    public static Point createInitial(User user) {
        Point point = new Point();
        point.user = user;
        point.balance = 0;
        point.version = 0;
        point.integrityHash = PointIntegrityHasher.hash(user.getId(), 0, 0);
        return point;
    }

    // == 비즈니스 메서드 ==

    // 포인트 증가
    // 충전, 적립, 복구 시 사용 -> 해시 재계산
    public void increaseBalance(Integer amount) {
        validatePositiveAmount(amount);
        this.balance += amount;
        regenerateHash();
    }

    // 포인트 감소
    // 결제 사용, 만료 처리 시 사용
    public void decreaseBalance(Integer amount) {
        validatePositiveAmount(amount);

        if (this.balance < amount) {
            throw new IllegalArgumentException("보유 포인트가 부족합니다.");
        }

        this.balance -= amount;
        regenerateHash();
    }


    // 무결성 검증 - 조회 시점에 호출
    public void verifyIntegrity() {
        String expectedHash = PointIntegrityHasher.hash(user.getId(), balance, version);
        if(!expectedHash.equals(this.integrityHash)) {
            throw new CustomException(ErrorCode.POINT_010,
                String.format("포인트 무결성 위반 감지, userId=%d, balance=%d, version=%d",
                    user.getId(), balance, version));
        }
    }

    // 내부
    // 해시 재계산
    // version +1 로 미리 계산하는 이유
    // : increase/decrease 직후 호출됨
    // : JPA가 flush 시 UPDATE 발행하며 version을 +1함
    // : flush 후 시점의 version을 미리 예측해서 해시를 장착
    // : 다음 트랜잭션 SELECT 시 : DB의 version과 해시 입력의 version이 일치 -> verify 통과
    private void regenerateHash() {
        // version은 @Version 변경 시 자동 증가 -> 미리 +1 해서 해시 생성
        // JPA가 dirty checking으로 UPDATE 발행 시 version도 +1되어 해시와 일치)
        this.integrityHash = PointIntegrityHasher.hash(user.getId(), balance, version +1 );
    }

    private void validatePositiveAmount(Integer amount) {
        if(amount == null || amount <= 0) {
            throw new CustomException(ErrorCode.POINT_003, "포인트 변동 양수여야 합니다:" + amount);
        }
    }

}
