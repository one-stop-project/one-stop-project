package com.sparta.one_stop.global.outbox.entity;

import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.outbox.OutboxEventStatus;
import com.sparta.one_stop.global.enums.outbox.OutboxEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "outbox_event",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_outbox_event_event_id",
            columnNames = "event_id"
        )
    },
    indexes = {
        @Index(
            name = "idx_outbox_status_created",
            columnList = "status, created_at"
        ),
        @Index(
            name = "idx_outbox_cleanup",
            columnList = "status, processed_at"
        ),
        @Index(
            name = "idx_outbox_aggregate",
            columnList = "aggregate_type, aggregate_id"
        ),
        @Index(
            name = "idx_outbox_event_type",
            columnList = "event_type, created_at"
        )
    }
)
public class OutboxEvent extends BaseEntity {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private static final String PAYMENT_APPROVED_TOPIC = "payment.approved";
    private static final String ORDER_AGGREGATE_TYPE = "ORDER";
    private static final String DELIVERY_COMPLETED_TOPIC = "delivery.completed";
    private static final String DELIVERY_AGGREGATE_TYPE = "DELIVERY";

    // Outbox 이벤트 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long id;

    // 이벤트 고유 ID
    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    // 이벤트 타입
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private OutboxEventType eventType;

    // 이벤트 기준 도메인
    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    // 이벤트 기준 도메인 ID
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    // Kafka 전송 대상 토픽
    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    // Kafka 메시지 Key
    @Column(name = "partition_key", nullable = false, length = 100)
    private String partitionKey;

    // Kafka로 전송할 JSON Payload
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    // Outbox 이벤트 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    // Kafka 발행 실패 재시도 횟수
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    // 마지막 발행 실패 사유
    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    // 발행 성공 또는 최종 실패 처리 일시
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // == 생성자 ==
    private OutboxEvent(
        String eventId,
        OutboxEventType eventType,
        String aggregateType,
        Long aggregateId,
        String topic,
        String partitionKey,
        String payload
    ) {
        validateRequiredFields(
            eventId,
            eventType,
            aggregateType,
            aggregateId,
            topic,
            partitionKey,
            payload
        );

        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.retryCount = 0;
    }

    // == 생성 메서드 ==

    public static OutboxEvent create(
        String eventId,
        OutboxEventType eventType,
        String aggregateType,
        Long aggregateId,
        String topic,
        String partitionKey,
        String payload
    ) {
        return new OutboxEvent(
            eventId,
            eventType,
            aggregateType,
            aggregateId,
            topic,
            partitionKey,
            payload
        );
    }

    public static OutboxEvent paymentApproved(
        String eventId,
        Long orderId,
        String payload
    ) {
        return new OutboxEvent(
            eventId,
            OutboxEventType.PAYMENT_APPROVED,
            ORDER_AGGREGATE_TYPE,
            orderId,
            PAYMENT_APPROVED_TOPIC,
            String.valueOf(orderId),
            payload
        );
    }

    public static OutboxEvent deliveryCompleted(
        String eventId,
        Long orderId,
        String payload
    ) {
        return new OutboxEvent(
            eventId,
            OutboxEventType.DELIVERY_COMPLETED,
            DELIVERY_AGGREGATE_TYPE,
            orderId,
            DELIVERY_COMPLETED_TOPIC,
            String.valueOf(orderId),
            payload
        );
    }

    // == 비즈니스 메서드 ==

    // Publisher가 이벤트 발행을 위해 선점 처리
    public void markProcessing() {
        if (this.status != OutboxEventStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태의 이벤트만 PROCESSING 처리할 수 있습니다.");
        }

        this.status = OutboxEventStatus.PROCESSING;
        this.processedAt = null;
    }

    // Kafka 발행 성공 처리
    public void markPublished() {
        if (this.status != OutboxEventStatus.PROCESSING) {
            throw new IllegalStateException("PROCESSING 상태의 이벤트만 PUBLISHED 처리할 수 있습니다.");
        }

        this.status = OutboxEventStatus.PUBLISHED;
        this.processedAt = LocalDateTime.now();
    }

    // Kafka 발행 실패 처리 (재시도 한도에 따라 PENDING 또는 DEAD로 전이)
    public void markFailure(String errorMessage, int maxRetryCount) {
        if (this.status != OutboxEventStatus.PROCESSING) {
            throw new IllegalStateException("PROCESSING 상태의 이벤트만 실패 처리할 수 있습니다.");
        }

        if (maxRetryCount < 0) {
            throw new IllegalArgumentException("최대 재시도 횟수는 0 이상이어야 합니다.");
        }

        this.retryCount++;
        this.lastErrorMessage = trimErrorMessage(errorMessage);

        if (this.retryCount > maxRetryCount) {
            this.status = OutboxEventStatus.DEAD;
            this.processedAt = LocalDateTime.now();
        } else {
            this.status = OutboxEventStatus.PENDING;
            this.processedAt = null;
        }
    }

    // OutboxEvent 생성에 필요한 모든 필수 필드를 검증한다
    private void validateRequiredFields(
        String eventId,
        OutboxEventType eventType,
        String aggregateType,
        Long aggregateId,
        String topic,
        String partitionKey,
        String payload
    ) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("이벤트 ID는 필수입니다.");
        }

        if (eventType == null) {
            throw new IllegalArgumentException("이벤트 타입은 필수입니다.");
        }

        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("Aggregate Type은 필수입니다.");
        }

        if (aggregateId == null) {
            throw new IllegalArgumentException("Aggregate ID는 필수입니다.");
        }

        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Kafka Topic은 필수입니다.");
        }

        if (partitionKey == null || partitionKey.isBlank()) {
            throw new IllegalArgumentException("Partition Key는 필수입니다.");
        }

        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("이벤트 Payload는 필수입니다.");
        }
    }

    // 에러 메시지를 컬럼 길이 제한(500자)에 맞게 자른다
    private String trimErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }

        if (errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }

        return errorMessage.substring(
            0,
            MAX_ERROR_MESSAGE_LENGTH
        );
    }

}
