package com.sparta.one_stop.domain.ai.event;

import com.sparta.one_stop.domain.ai.service.AiReviewSummaryService;
import com.sparta.one_stop.domain.review.event.ReviewCreatedEvent;
import com.sparta.one_stop.domain.review.event.ReviewSummaryRefreshEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 리뷰 이벤트를 수신해 AI 요약을 갱신합니다.
 *
 * @Async("eventExecutor"): 백그라운드 작업 → Drop 정책 풀 사용 (API 응답 블로킹 방지)
 * AFTER_COMMIT: DB 커밋 성공 후에만 실행
 *
 * 동시성 처리:
 *   - 최초 생성 경쟁(unique 제약 위반): DataIntegrityViolationException → INFO 로그 후 무시
 *   - 낙관적 락 충돌: 1회 재시도 후 실패 시 WARN 로그 → 다음 이벤트에서 자연 보정
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewSummaryUpdateListener {

    private final AiReviewSummaryService aiReviewSummaryService;

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCreated(ReviewCreatedEvent event) {
        try {
            aiReviewSummaryService.updateIncrementalSummary(event.productId(), event.reviewId());
        } catch (DataIntegrityViolationException e) {
            log.info("[ReviewSummaryUpdate] 최초 생성 경쟁 조건, 선착순 처리됨: productId={}", event.productId());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("[ReviewSummaryUpdate] 낙관적 락 충돌, 1회 재시도: productId={}", event.productId());
            retryIncremental(event.productId(), event.reviewId());
        } catch (Exception e) {
            log.error("[ReviewSummaryUpdate] 증분 요약 실패: productId={}, reviewId={}",
                event.productId(), event.reviewId(), e);
        }
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleModified(ReviewSummaryRefreshEvent event) {
        try {
            aiReviewSummaryService.refreshSummaryAsync(event.productId());
        } catch (Exception e) {
            log.error("[ReviewSummaryUpdate] 수정/삭제 후 재요약 실패: productId={}", event.productId(), e);
        }
    }

    private void retryIncremental(Long productId, Long reviewId) {
        try {
            aiReviewSummaryService.updateIncrementalSummary(productId, reviewId);
        } catch (Exception e) {
            log.warn("[ReviewSummaryUpdate] 재시도 실패, 다음 이벤트에서 보정: productId={}", productId);
        }
    }
}
