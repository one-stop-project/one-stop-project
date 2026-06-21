package com.sparta.one_stop.domain.seller.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.seller.dashboard")
public class SellerDashboardProperties {

    private long defaultRangeDays = 31L;
    private long maxRangeDays = 31L;
    private String zoneId = "Asia/Seoul";
    private int maxPageSize = 100;
}
