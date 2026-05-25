package com.sparta.one_stop.global.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;

/**
 * ═══════════════════════════════════════════════════════════
 *  정책 선택 가이드
 * ═══════════════════════════════════════════════════════════
 *
 *  drop + log + 메트릭:
 *    - 응답 시간 절대 영향 안 줘야 할 때
 *    - 작업 누락 허용 가능 (예: 로그 적재, 알림 발송)
 *    - 우리 케이스: AllDevicesLogoutEvent (Redis 정리)
 *      → 누락돼도 14일 후 TTL로 정리됨
 *
 *  CallerRunsPolicy + backpressure:
 *    - 작업 누락 안 됨
 *    - 응답 시간 영향 감수 가능
 *    - 우리 케이스: AI 호출 (사용자에게 응답 의무)
 */
@Slf4j
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

    private final MeterRegistry meterRegistry;

    @Override
    public Executor getAsyncExecutor() {
        return eventExecutor();
    }

    /**
     * 이벤트 처리 전용 스레드풀 — Fire-and-Forget
     *
     * 정책: 큐 포화 시 작업 드롭 + 메트릭 기록
     * 이유: 이벤트는 부수 효과 처리 — API 응답 시간 보호 우선
     *       누락된 이벤트는 다음 트리거 또는 TTL로 자연 정리
     */
    @Bean("eventExecutor")
    public Executor eventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("event-");

        // ★ 정책: 드롭 + 메트릭 (Fire-and-Forget 진짜 보장)
        executor.setRejectedExecutionHandler(dropWithMetricsPolicy("event"));

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * AI 호출 전용 스레드풀 — 작업 누락 안 됨
     *
     * 정책: 큐 포화 시 호출 스레드가 직접 실행 (backpressure)
     * 이유: AI 호출은 사용자에게 응답 의무 있음
     *       단, 호출 측에서 응답 시간 지연 가능성 알고 있어야 함
     */
    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ai-");

        // ★ 정책: CallerRunsPolicy + 메트릭 (backpressure 가시화)
        executor.setRejectedExecutionHandler(callerRunsWithMetricsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * 비동기 예외 핸들러 — 운영 가시성
     */
    @Override
    public org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("[Async 예외] method={}", method.getName(), ex);

            Counter.builder("async.exception")
                .tag("method", method.getName())
                .description("Async 메서드 미처리 예외 횟수")
                .register(meterRegistry)
                .increment();
        };
    }

    // ═══════════════════════════════════════════════════════════
    //  Rejection Policies
    // ═══════════════════════════════════════════════════════════

    /**
     * 드롭 + 메트릭 정책 — Fire-and-Forget용
     */
    private RejectedExecutionHandler dropWithMetricsPolicy(String poolName) {
        return (task, executor) -> {
            log.warn("[Async Rejection] 큐 포화 — 작업 드롭: pool={}, queueSize={}, activeCount={}",
                poolName, executor.getQueue().size(), executor.getActiveCount());

            Counter.builder("async.rejection")
                .tag("pool", poolName)
                .tag("policy", "drop")
                .description("스레드풀 큐 포화로 드롭된 작업 수")
                .register(meterRegistry)
                .increment();
        };
    }

    /**
     * CallerRuns + 메트릭 정책 — Backpressure용
     */
    private RejectedExecutionHandler callerRunsWithMetricsPolicy() {
        return (task, executor) -> {
            log.warn("[Async Rejection] 큐 포화 — 호출 스레드 직접 실행 (backpressure)");

            Counter.builder("async.rejection")
                .tag("pool", "ai")
                .tag("policy", "caller-runs")
                .description("스레드풀 큐 포화로 backpressure 발생 횟수")
                .register(meterRegistry)
                .increment();

            // 호출 스레드가 직접 실행
            if (!executor.isShutdown()) {
                task.run();
            }
        };
    }
}
