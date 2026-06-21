package com.sparta.one_stop.domain.seller.service;

import com.sparta.one_stop.domain.seller.config.SellerDashboardProperties;
import com.sparta.one_stop.domain.seller.dto.response.SellerOrderStatusCountResponse;
import com.sparta.one_stop.domain.seller.dto.response.SellerProductSalesStatResponse;
import com.sparta.one_stop.domain.seller.repository.SellerDashboardQueryRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerDashboardService {

    private final SellerReader sellerReader;
    private final SellerDashboardQueryRepository queryRepository;
    private final SellerDashboardProperties properties;
    private final SellerPagePolicy pagePolicy;

    public SellerOrderStatusCountResponse getOrderStatusCounts(Long userId) {
        Seller seller = sellerReader.getApprovedSeller(userId);
        Map<OrderItemStatus, Long> counts = queryRepository
            .countByStatus(seller.getId(), OrderItemStatus.PENDING_PAYMENT)
            .stream()
            .collect(Collectors.toMap(
                row -> (OrderItemStatus) row[0],
                row -> ((Number) row[1]).longValue(),
                (left, right) -> left,
                () -> new EnumMap<>(OrderItemStatus.class)
            ));
        return SellerOrderStatusCountResponse.from(counts);
    }

    /**
     * 주문 생성일 기준 기간 안에 들어오며 현재 배송 완료 상태인 주문상품을 집계한다.
     * OrderItem에는 배송완료 시각이 없으므로 이 값을 배송완료일 기준 통계로 해석하면 안 된다.
     */
    public Page<SellerProductSalesStatResponse> getProductSalesStats(
        Long userId, LocalDate from, LocalDate to, Pageable pageable
    ) {
        pagePolicy.validate(pageable);
        Seller seller = sellerReader.getApprovedSeller(userId);
        DateRange range = resolveRange(from, to);
        return queryRepository.findProductSalesStats(
            seller.getId(), range.fromInclusive(), range.toExclusive(), pageable);
    }

    DateRange resolveRange(LocalDate from, LocalDate to) {
        long maxDays = Math.max(1L, properties.getMaxRangeDays());
        long defaultDays = Math.min(Math.max(1L, properties.getDefaultRangeDays()), maxDays);
        LocalDate resolvedTo = to == null ? LocalDate.now(resolveZoneId()) : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minusDays(defaultDays - 1) : from;
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new CustomException(ErrorCode.COMMON_010, "from은 to보다 이후일 수 없습니다");
        }
        if (ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) + 1 > maxDays) {
            throw new CustomException(ErrorCode.COMMON_010, "상품 판매 통계 조회 기간을 초과했습니다");
        }
        return new DateRange(resolvedFrom.atStartOfDay(), resolvedTo.plusDays(1).atStartOfDay());
    }

    private ZoneId resolveZoneId() {
        try {
            return ZoneId.of(properties.getZoneId());
        } catch (DateTimeException e) {
            throw new CustomException(ErrorCode.COMMON_010, "판매자 대시보드 타임존 설정이 올바르지 않습니다");
        }
    }

    record DateRange(LocalDateTime fromInclusive, LocalDateTime toExclusive) { }
}
