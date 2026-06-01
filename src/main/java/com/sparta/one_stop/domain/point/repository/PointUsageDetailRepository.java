package com.sparta.one_stop.domain.point.repository;

import com.sparta.one_stop.domain.point.entity.PointUsageDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PointUsageDetailRepository extends JpaRepository<PointUsageDetail, Long> {

    // 포인트 사용 이력 ID 기준 사용 상세 조회
    // 하나의 USE 이력이 어떤 원본 포인트 이력들에서 차감되었는지 확인할 때 사용한다
    List<PointUsageDetail> findAllByUseHistoryId(Long useHistoryId);

    // 여러 포인트 사용 이력 ID 기준 사용 상세 일괄 조회
    // 주문 취소 시 해당 주문의 USE 이력들을 한 번에 조회한 뒤 복구 처리할 때 사용한다
    List<PointUsageDetail> findAllByUseHistoryIdIn(Collection<Long> useHistoryIds);

}
