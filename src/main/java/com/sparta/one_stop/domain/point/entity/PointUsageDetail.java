package com.sparta.one_stop.domain.point.entity;

import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.point.PointHistoryType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "point_usage_detail",
    indexes = {
        @Index(name = "idx_pud_use_history", columnList = "use_history_id"),
        @Index(name = "idx_pud_source_history", columnList = "source_history_id")
    }
)
public class PointUsageDetail extends BaseEntity {

    // 포인트 사용 상세 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usage_detail_id")
    private Long id;

    // 포인트 사용 이력
    // 반드시 PointHistoryType.USE 타입의 이력과 연결된다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "use_history_id", nullable = false)
    private PointHistory useHistory;

    // 실제 차감된 원본 포인트 이력
    // CHARGE / EARN / REFUND 타입의 PointHistory와 연결된다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_history_id", nullable = false)
    private PointHistory sourceHistory;

    // 해당 원본 포인트 이력에서 실제 차감한 금액
    @Column(name = "used_amount", nullable = false)
    private Integer usedAmount;

    // 원본 포인트의 만료일
    // 주문 취소 시 원래 만료일 기준으로 복구 가능 여부를 판단하기 위해 저장한다
    @Column(name = "source_expire_at", nullable = false)
    private LocalDate sourceExpireAt;

    // == 생성자 ==
    public PointUsageDetail(
        PointHistory useHistory,
        PointHistory sourceHistory,
        Integer usedAmount
    ) {
        validate(
            useHistory,
            sourceHistory,
            usedAmount
        );

        this.useHistory = useHistory;
        this.sourceHistory = sourceHistory;
        this.usedAmount = usedAmount;
        this.sourceExpireAt = sourceHistory.getExpireAt();
    }

    // == 검증 메서드 ==

    private void validate(
        PointHistory useHistory,
        PointHistory sourceHistory,
        Integer usedAmount
    ) {
        if (useHistory == null) {
            throw new CustomException(ErrorCode.POINT_035);
        }

        if (sourceHistory == null) {
            throw new CustomException(ErrorCode.POINT_036);
        }

        if (useHistory.getType() != PointHistoryType.USE) {
            throw new CustomException(ErrorCode.POINT_037);
        }

        if (!sourceHistory.isDeductibleSource()) {
            throw new CustomException(ErrorCode.POINT_038);
        }

        if (usedAmount == null || usedAmount <= 0) {
            throw new CustomException(ErrorCode.POINT_039);
        }

        if (sourceHistory.getRemainingAmount() == null
            || sourceHistory.getRemainingAmount() < usedAmount) {
            throw new CustomException(ErrorCode.POINT_040);
        }

        if (sourceHistory.getExpireAt() == null) {
            throw new CustomException(ErrorCode.POINT_041);
        }
    }

}
