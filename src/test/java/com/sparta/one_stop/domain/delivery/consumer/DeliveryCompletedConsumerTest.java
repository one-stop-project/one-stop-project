package com.sparta.one_stop.domain.delivery.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.domain.delivery.event.DeliveryCompletedEventPayload;
import com.sparta.one_stop.domain.point.service.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryCompletedConsumerTest {

    private final ObjectMapper objectMapper =
        new ObjectMapper().findAndRegisterModules();

    @Mock
    private PointService pointService;

    private DeliveryCompletedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DeliveryCompletedConsumer(
            pointService,
            objectMapper
        );
    }

    @Test
    @DisplayName("배송 완료 이벤트 수신 성공 - 포인트 적립 호출")
    void onDeliveryCompleted_success() throws Exception {

        // given
        DeliveryCompletedEventPayload payload =
            DeliveryCompletedEventPayload.of(
                "event-1",
                100L,
                1L,
                10L,
                LocalDateTime.now()
            );

        String json =
            objectMapper.writeValueAsString(payload);

        // when
        consumer.onDeliveryCompleted(json);

        // then
        verify(pointService)
            .earnPointByDelivery(100L);
    }

    @Test
    @DisplayName("역직렬화 실패 시 포인트 적립을 호출하지 않는다")
    void onDeliveryCompleted_fail_deserialize() {

        // given
        String invalidJson = "not-json";

        // when
        consumer.onDeliveryCompleted(invalidJson);

        // then
        verify(pointService, never())
            .earnPointByDelivery(anyLong());
    }

    @Test
    @DisplayName("포인트 적립 실패 시 예외를 전파한다")
    void onDeliveryCompleted_fail_pointEarn() throws Exception {

        // given
        DeliveryCompletedEventPayload payload =
            DeliveryCompletedEventPayload.of(
                "event-1",
                100L,
                1L,
                10L,
                LocalDateTime.now()
            );

        String json =
            objectMapper.writeValueAsString(payload);

        doThrow(new RuntimeException("point fail"))
            .when(pointService)
            .earnPointByDelivery(100L);

        // when & then
        assertThatThrownBy(() ->
            consumer.onDeliveryCompleted(json)
        )
            .isInstanceOf(RuntimeException.class)
            .hasMessage("point fail");
    }
}
