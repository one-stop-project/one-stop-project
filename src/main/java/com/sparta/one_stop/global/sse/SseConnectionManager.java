package com.sparta.one_stop.global.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SseConnectionManager {

    // SseEmitter 타임아웃: 30분
    private static final long TIMEOUT = 30 * 60 * 1000L;

    // userId별 SseEmitter 관리
    // 사용자당 1개의 연결만 유지한다
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    // SSE 연결 생성
    // 동일 사용자가 재연결하면 기존 emitter를 교체한다
    // 연결 성공 시 초기 이벤트(connect)를 전송하여 연결 성공을 알린다
    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);

        emitter.onCompletion(() -> {
            log.debug("SSE 연결 완료 - userId: {}", userId);
            emitters.remove(userId);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE 연결 타임아웃 - userId: {}", userId);
            emitters.remove(userId);
        });

        emitter.onError(e -> {
            log.warn("SSE 연결 에러 - userId: {}", userId, e);
            emitters.remove(userId);
        });

        emitters.put(userId, emitter);

        sendInitialEvent(userId, emitter);

        log.info("SSE 연결 성공 - userId: {}, 현재 연결 수: {}", userId, emitters.size());

        return emitter;
    }

    // 특정 사용자에게 SSE 알림 전송
    // 연결이 없는 사용자에게는 전송을 시도하지 않는다
    // 전송 실패 시 해당 emitter를 제거하고 로그를 기록한다
    public void send(Long userId, Object data) {
        SseEmitter emitter = emitters.get(userId);

        if (emitter == null) {
            log.debug("SSE 연결 없음 - userId: {}, 전송 스킵", userId);
            return;
        }

        try {
            emitter.send(
                SseEmitter.event()
                    .name("notification")
                    .data(data)
            );

            log.debug("SSE 알림 전송 성공 - userId: {}", userId);
        } catch (IOException e) {
            log.warn("SSE 알림 전송 실패 - userId: {}", userId, e);
            emitters.remove(userId);
        }
    }

    // SSE 연결 해제
    public void disconnect(Long userId) {
        SseEmitter emitter = emitters.remove(userId);

        if (emitter != null) {
            emitter.complete();
            log.debug("SSE 연결 해제 - userId: {}", userId);
        }
    }

    // 연결 직후 초기 이벤트 전송
    // SSE 스펙상 연결 후 데이터를 보내지 않으면 타임아웃이 발생할 수 있다
    private void sendInitialEvent(Long userId, SseEmitter emitter) {
        try {
            emitter.send(
                SseEmitter.event()
                    .name("connect")
                    .data("SSE 연결 성공")
            );
        } catch (IOException e) {
            log.warn("SSE 초기 이벤트 전송 실패 - userId: {}", userId, e);
            emitters.remove(userId);
        }
    }

}
