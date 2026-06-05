package com.sparta.one_stop.domain.ai.event;

import com.sparta.one_stop.domain.ai.service.AiReviewSummaryService;
import com.sparta.one_stop.domain.review.event.ReviewCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 리뷰 작성 이벤트를 수신해 AI 요약을 증분 업데이트합니다.
 *
 * @Async("eventExecutor"): 백그라운드 작업이므로 Drop 정책 풀 사용
 *   - CallerRunsPolicy(aiExecutor)를 쓰면 큐 포화 시 API 요청 스레드가 AI 요약(수 초)을 직접 실행해 응답 시간 블로킹 발생
 *   - 드롭되어도 낙관적 락 충돌과 동일하게 다음 리뷰 이벤트에서 자연 보정됨
 * AFTER_COMMIT: DB 트랜잭션 커밋 성공 후에만 실행 (리뷰 저장 실패 시 요약 업데이트 안 함)
 * OptimisticLockException: 동시 업데이트 충돌 시 로그만 남기고 무시 — 다음 리뷰 이벤트에서 자연 보정
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewSummaryUpdateListener {

    private final AiReviewSummaryService aiReviewSummaryService;

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ReviewCreatedEvent event) {
        try {
            aiReviewSummaryService.updateIncrementalSummary(event.productId(), event.reviewId());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("[ReviewSummaryUpdate] 동시 업데이트 충돌, 다음 이벤트에서 보정: productId={}", event.productId());
        } catch (Exception e) {
            log.error("[ReviewSummaryUpdate] 증분 요약 실패: productId={}, reviewId={}",
                event.productId(), event.reviewId(), e);
        }
    }
}
