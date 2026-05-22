package com.sparta.one_stop.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 처리 설정
 *
 * 스레드풀 분리 전략:
 *
 *   1. event-executor (기본)
 *      - 이벤트 리스너 (AllDevicesLogoutEventListener 등)
 *      - 응답 시간에 영향 없는 부수 작업
 *      - 풀 크기: 작게 (메인 스레드 보호)
 *
 *   2. ai-executor (향후 AI 도입 시)
 *      - Spring AI 호출 격리
 *      - LLM 응답 지연(10초+)이 다른 API에 영향 안 주도록
 *      - Circuit Breaker와 함께 사용
 *
 * 거부 정책:
 *   - CallerRunsPolicy: 큐 가득 차면 호출 스레드가 직접 실행
 *   - 작업 손실 방지 + 자연스러운 backpressure
 *
 * 트래픽 시 동작 예측 (1,000 RPS):
 *   - 비밀번호 변경 100건/분 = 이벤트 100건/분
 *   - core=2, max=4, queue=100 → 충분히 여유
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        return eventExecutor();
    }

    /**
     * 이벤트 처리 전용 스레드풀
     */
    @Bean("eventExecutor")
    public Executor eventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("event-");

        // 큐 가득 차면 호출 스레드가 직접 실행 (작업 손실 방지)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Spring 컨텍스트 종료 시 진행 중 작업 완료 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }

    /**
     * AI 호출 전용 스레드풀 (향후 활용)
     */
    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ai-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
