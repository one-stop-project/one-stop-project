package com.sparta.one_stop.domain.product.scheduler;

import com.sparta.one_stop.domain.product.service.PopularTagService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopularTagScheduler {

    private static final long ONE_HOUR_MS = 60L * 60 * 1000;

    private final PopularTagService popularTagService;

    @PostConstruct
    public void init() {
        popularTagService.refresh();
    }

    @Scheduled(fixedRate = ONE_HOUR_MS)
    public void refresh() {
        popularTagService.refresh();
    }
}
