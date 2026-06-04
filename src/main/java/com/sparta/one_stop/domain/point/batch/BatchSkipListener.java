package com.sparta.one_stop.domain.point.batch;

import com.sparta.one_stop.domain.point.entity.PointHistory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;

/**
 * 배치 처리 중 스킵된 항목 추적
 *
 * <p>{@code faultTolerant + skipLimit} 설정 시 호출됨.
 * <br>운영에서는 이 로그를 Slack/Sentry로 보내 즉시 알림.
 */
@Slf4j
public class BatchSkipListener implements SkipListener<PointHistory, Object> {

    @Override
    public void onSkipInRead(Throwable t) {
        log.error("[POINT_EXPIRE_BATCH] Read 단계 스킵: {}", t.getMessage(), t);
    }

    @Override
    public void onSkipInProcess(PointHistory item, Throwable t) {
        log.error("[POINT_EXPIRE_BATCH] Process 단계 스킵: pointHistoryId={}, amount={}, error={}",
            item.getId(), item.getRemainingAmount(), t.getMessage(), t);
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        log.error("[POINT_EXPIRE_BATCH] Write 단계 스킵: item={}, error={}",
            item, t.getMessage(), t);
    }
}

