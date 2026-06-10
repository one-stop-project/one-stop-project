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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublishExecutor {

    private static final int MAX_RETRY_COUNT = 3;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxKafkaProducer outboxKafkaProducer;
    private final SlackAlertService slackAlertService;

    /**
     * 개별 Outbox 이벤트 발행 처리
     *
     * 처리 흐름:
     * 1. PENDING 상태 이벤트를 PROCESSING 상태로 조건부 선점한다.
     * 2. 선점에 성공한 이벤트만 Kafka로 발행한다.
     * 3. Kafka 발행 성공 시 PUBLISHED 상태로 변경한다.
     * 4. Kafka 발행 실패 시 retryCount를 증가시키고 재시도 가능 여부에 따라 PENDING 또는 DEAD 상태로 변경한다.
     *
     * 트랜잭션 정책:
     * - 이벤트 1건마다 독립 트랜잭션으로 처리한다.
     * - 한 이벤트의 발행 실패가 다른 이벤트 처리에 영향을 주지 않도록 한다.
     * - DEAD 상태 저장은 반드시 DB 트랜잭션 안에서 처리한다.
     * - Slack 알림은 외부 네트워크 I/O이므로 DB 트랜잭션 커밋 이후에 수행한다.
     */
    @Transactional
    public void execute(OutboxEvent event) {
        // PENDING → PROCESSING 조건부 선점
        // affected row가 1이면 선점 성공, 0이면 다른 Publisher가 이미 선점한 것으로 판단한다.
        int updated = outboxEventRepository.updateStatusByIdAndStatus(
            event.getId(),
            OutboxEventStatus.PENDING,
            OutboxEventStatus.PROCESSING
        );

        if (updated == 0) {
            log.debug(
                "Outbox 이벤트 선점 실패 (이미 선점됨) - eventId: {}",
                event.getEventId()
            );
            return;
        }

        // 조건부 UPDATE 이후 최신 상태의 이벤트를 다시 조회한다.
        // updateStatusByIdAndStatus()에서 clearAutomatically가 적용되는 경우,
        // 기존 엔티티 상태를 신뢰하지 않고 DB 기준 상태를 다시 읽는다.
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
            processingEvent.markFailure(
                e.getMessage(),
                MAX_RETRY_COUNT
            );

            if (processingEvent.getStatus() == OutboxEventStatus.DEAD) {
                log.error(
                    "Outbox 이벤트 DEAD 전이 - eventId: {}, retryCount: {}, 수동 확인 필요",
                    processingEvent.getEventId(),
                    processingEvent.getRetryCount()
                );

                // Slack 알림은 외부 API 호출이므로 현재 트랜잭션 안에서 직접 호출하지 않는다.
                // Slack 응답 지연이 DB 커넥션 점유로 이어지지 않도록 커밋 이후 실행한다.
                sendSlackAlertAfterCommit(processingEvent);
            } else {
                log.warn(
                    "Outbox 이벤트 발행 실패 (재시도 예정) - eventId: {}, retryCount: {}",
                    processingEvent.getEventId(),
                    processingEvent.getRetryCount()
                );
            }
        }

        // PUBLISHED, PENDING, DEAD 상태 변경 결과를 저장한다.
        // Slack 알림은 이 저장이 커밋된 이후에만 실행된다.
        outboxEventRepository.save(processingEvent);
    }

    /**
     * Outbox DEAD Slack 알림을 트랜잭션 커밋 이후에 실행하도록 등록한다.
     *
     * 이유:
     * - Slack Webhook 호출은 외부 네트워크 I/O이다.
     * - 트랜잭션 안에서 동기 호출하면 Slack 응답 지연 동안 DB 커넥션을 점유할 수 있다.
     * - Slack 전송 실패가 OutboxEvent 상태 저장에 영향을 주면 안 된다.
     *
     * 동작:
     * - 트랜잭션 동기화가 활성화되어 있으면 afterCommit 콜백에 Slack 알림을 등록한다.
     * - 트랜잭션 동기화가 없는 테스트/예외적 호출 환경에서는 즉시 전송한다.
     */
    private void sendSlackAlertAfterCommit(OutboxEvent outboxEvent) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            slackAlertService.sendOutboxDeadAlert(outboxEvent);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    slackAlertService.sendOutboxDeadAlert(outboxEvent);
                }
            }
        );
    }

}
