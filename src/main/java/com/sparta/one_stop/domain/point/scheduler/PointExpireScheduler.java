package com.sparta.one_stop.domain.point.scheduler;

import com.sparta.one_stop.domain.point.service.PointExpireService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointExpireScheduler {

    private final PointExpireService pointExpireService;

    /**
     * 포인트 만료 스케줄러
     * - 매월 1일 새벽 3시에 실행
     * - DB의 PointHistory 기준으로 만료 대상 포인트 처리
     */
    @Scheduled(cron = "0 0 3 1 * *")
    public void expirePoints() {
        LocalDate today = LocalDate.now();

        int expiredAmount = pointExpireService.expirePoints(today);

        log.info(
            "포인트 만료 스케줄러 실행 완료 - 기준일: {}, 만료 포인트 총액: {}",
            today,
            expiredAmount
        );
    }

}
