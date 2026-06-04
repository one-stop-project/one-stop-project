package com.sparta.one_stop.global.outbox.publisher;

import com.sparta.one_stop.global.enums.outbox.OutboxEventStatus;
import com.sparta.one_stop.global.outbox.entity.OutboxEvent;
import com.sparta.one_stop.global.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPublishExecutor outboxEventPublishExecutor;

    // 1초 주기로 PENDING 상태 이벤트를 폴링하여 Kafka로 발행한다
    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
            OutboxEventStatus.PENDING,
            PageRequest.of(0, BATCH_SIZE)
        );

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Outbox 폴링 - PENDING 이벤트 {}건 조회", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            outboxEventPublishExecutor.execute(event);
        }
    }

}
