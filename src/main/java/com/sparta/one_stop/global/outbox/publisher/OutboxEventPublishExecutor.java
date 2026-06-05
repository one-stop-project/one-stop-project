package com.sparta.one_stop.global.outbox.publisher;

import com.sparta.one_stop.global.alert.slack.SlackAlertService;
import com.sparta.one_stop.global.enums.outbox.OutboxEventStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.outbox.entity.OutboxEvent;
import com.sparta.one_stop.global.outbox.kafka.OutboxKafkaProducer;
import com.sparta.one_stop.global.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublishExecutor {

    private static final int MAX_RETRY_COUNT = 3;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxKafkaProducer outboxKafkaProducer;
    private final SlackAlertService slackAlertService;

    // 개별 Outbox 이벤트 선점 → Kafka 발행 → 상태 업데이트
    // 이벤트별 독립 트랜잭션으로 처리하여 한 건 실패가 다른 이벤트에 영향을 주지 않는다
    @Transactional
    public void execute(OutboxEvent event) {
        // PENDING → PROCESSING 조건부 선점 (DB atomic update)
        int updated = outboxEventRepository.updateStatusByIdAndStatus(
            event.getId(),
            OutboxEventStatus.PENDING,
            OutboxEventStatus.PROCESSING
        );

        // 다른 Publisher가 이미 선점한 경우 건너뛴다
        if (updated == 0) {
            log.debug("Outbox 이벤트 선점 실패 (이미 선점됨) - eventId: {}", event.getEventId());
            return;
        }

        // 선점 성공 후 최신 상태로 재조회 (clearAutomatically 이후 영속성 컨텍스트 갱신)
        OutboxEvent processingEvent = outboxEventRepository.findById(event.getId())
            .orElseThrow(() -> new CustomException(ErrorCode.OUTBOX_002));

        try {
            outboxKafkaProducer.send(
                processingEvent.getTopic(),
                processingEvent.getPartitionKey(),
                processingEvent.getPayload()
            );

            processingEvent.markPublished();

            log.info(
                "Outbox 이벤트 발행 성공 - eventId: {}, topic: {}",
                processingEvent.getEventId(),
                processingEvent.getTopic()
            );
        } catch (Exception e) {
            processingEvent.markFailure(e.getMessage(), MAX_RETRY_COUNT);

            if (processingEvent.getStatus() == OutboxEventStatus.DEAD) {
                log.error(
                    "Outbox 이벤트 DEAD 전이 - eventId: {}, retryCount: {}, 수동 확인 필요",
                    processingEvent.getEventId(),
                    processingEvent.getRetryCount()
                );

                slackAlertService.sendOutboxDeadAlert(processingEvent);
            } else {
                log.warn(
                    "Outbox 이벤트 발행 실패 (재시도 예정) - eventId: {}, retryCount: {}",
                    processingEvent.getEventId(),
                    processingEvent.getRetryCount()
                );
            }
        }

        outboxEventRepository.save(processingEvent);
    }

}
