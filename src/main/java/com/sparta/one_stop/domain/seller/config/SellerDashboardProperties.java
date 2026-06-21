package com.sparta.one_stop.domain.seller.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.ZoneId;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.seller.dashboard")
public class SellerDashboardProperties implements InitializingBean {

    private long defaultRangeDays = 31L;
    private long maxRangeDays = 31L;
    private String zoneId = "Asia/Seoul";
    private int maxPageSize = 100;

    @Override
    public void afterPropertiesSet() {
        if (defaultRangeDays < 1 || maxRangeDays < 1 || defaultRangeDays > maxRangeDays) {
            throw new IllegalStateException(
                "판매자 대시보드 조회 기간 설정은 1 이상이고 기본 기간이 최대 기간 이하여야 합니다");
        }
        if (maxPageSize < 1) {
            throw new IllegalStateException("판매자 대시보드 최대 페이지 크기는 1 이상이어야 합니다");
        }
        try {
            ZoneId.of(zoneId);
        } catch (DateTimeException | NullPointerException e) {
            throw new IllegalStateException("판매자 대시보드 타임존 설정이 올바르지 않습니다", e);
        }
    }
}
