package com.sparta.one_stop.domain.seller.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellerDashboardPropertiesTest {

    @Test
    void 기본값은_유효하다() {
        assertThatCode(new SellerDashboardProperties()::afterPropertiesSet)
            .doesNotThrowAnyException();
    }

    @Test
    void 기본_조회기간이_최대기간보다_크면_시작을_중단한다() {
        SellerDashboardProperties properties = new SellerDashboardProperties();
        properties.setDefaultRangeDays(32);
        properties.setMaxRangeDays(31);

        assertThatThrownBy(properties::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 최대_페이지_크기가_1보다_작으면_시작을_중단한다() {
        SellerDashboardProperties properties = new SellerDashboardProperties();
        properties.setMaxPageSize(0);

        assertThatThrownBy(properties::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 타임존이_유효하지_않으면_시작을_중단한다() {
        SellerDashboardProperties properties = new SellerDashboardProperties();
        properties.setZoneId("not-a-zone");

        assertThatThrownBy(properties::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class);
    }
}
