package com.sparta.one_stop.domain.seller.service;

import com.sparta.one_stop.domain.seller.config.SellerDashboardProperties;
import com.sparta.one_stop.domain.seller.repository.SellerDashboardQueryRepository;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SellerDashboardServiceTest {

    SellerDashboardProperties properties;
    SellerDashboardService service;

    @BeforeEach
    void setUp() {
        properties = new SellerDashboardProperties();
        properties.setDefaultRangeDays(31);
        properties.setMaxRangeDays(31);
        properties.setZoneId("Asia/Seoul");
        service = new SellerDashboardService(
            mock(SellerReader.class),
            mock(SellerDashboardQueryRepository.class),
            properties,
            mock(SellerPagePolicy.class)
        );
    }

    @Test
    void 종료일을_포함하도록_다음날_자정을_상한으로_사용한다() {
        var range = service.resolveRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 20));

        assertThat(range.fromInclusive()).isEqualTo("2026-06-01T00:00:00");
        assertThat(range.toExclusive()).isEqualTo("2026-06-21T00:00:00");
    }

    @Test
    void 최대_조회기간을_넘으면_잘못된요청_예외가_발생한다() {
        assertThatThrownBy(() -> service.resolveRange(
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 20)))
            .isInstanceOf(CustomException.class)
            .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_010));
    }

    @Test
    void 잘못된_타임존은_잘못된요청_예외로_변환한다() {
        properties.setZoneId("not-a-zone");

        assertThatThrownBy(() -> service.resolveRange(null, null))
            .isInstanceOf(CustomException.class)
            .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_010));
    }
}
