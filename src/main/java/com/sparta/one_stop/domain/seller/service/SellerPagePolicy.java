package com.sparta.one_stop.domain.seller.service;

import com.sparta.one_stop.domain.seller.config.SellerDashboardProperties;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SellerPagePolicy {

    private final SellerDashboardProperties properties;

    public void validate(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getPageSize() < 1
            || pageable.getPageSize() > Math.max(1, properties.getMaxPageSize())
            || pageable.getOffset() > Integer.MAX_VALUE) {
            throw new CustomException(ErrorCode.COMMON_010, "판매자 조회 페이지 범위가 올바르지 않습니다");
        }
    }
}
