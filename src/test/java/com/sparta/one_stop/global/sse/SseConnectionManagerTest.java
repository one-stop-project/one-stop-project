package com.sparta.one_stop.global.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SseConnectionManagerTest {

    private final SseConnectionManager sseConnectionManager =
        new SseConnectionManager();

    @Test
    @DisplayName("connect 성공 - SseEmitter를 반환한다")
    void connect_success() {
        // given
        Long userId = 1L;

        // when
        SseEmitter emitter = sseConnectionManager.connect(userId);

        // then
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("connect 성공 - 동일 사용자 재연결 시 기존 emitter를 교체한다")
    void connect_success_replacesPreviousEmitter() {
        // given
        Long userId = 1L;
        SseEmitter firstEmitter = sseConnectionManager.connect(userId);

        // when
        SseEmitter secondEmitter = sseConnectionManager.connect(userId);

        // then
        assertThat(secondEmitter).isNotNull();
        assertThat(secondEmitter).isNotSameAs(firstEmitter);

        assertThatCode(() -> sseConnectionManager.send(
            userId,
            "test-message"
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("send 성공 - 연결된 사용자에게 전송해도 예외가 발생하지 않는다")
    void send_success_whenUserIsConnected() {
        // given
        Long userId = 1L;
        sseConnectionManager.connect(userId);

        // when & then
        assertThatCode(() -> sseConnectionManager.send(
            userId,
            "test-message"
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("send 스킵 - 연결이 없는 사용자에게 전송해도 예외가 발생하지 않는다")
    void send_skip_whenUserIsNotConnected() {
        // given
        Long userId = 1L;

        // when & then
        assertThatCode(() -> sseConnectionManager.send(
            userId,
            "test-message"
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("disconnect 성공 - 연결을 해제한다")
    void disconnect_success() {
        // given
        Long userId = 1L;
        sseConnectionManager.connect(userId);

        // when
        sseConnectionManager.disconnect(userId);

        // then
        assertThatCode(() -> sseConnectionManager.send(
            userId,
            "test-message"
        )).doesNotThrowAnyException();
    }

}
