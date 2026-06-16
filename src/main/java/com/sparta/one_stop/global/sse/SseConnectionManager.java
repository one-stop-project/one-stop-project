package com.sparta.one_stop.global.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
@Component
public class SseConnectionManager {

    private static final long TIMEOUT = 30 * 60 * 1000L;

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * SseEmitter 생성자 주입용 Supplier
     *
     * 운영 환경에서는 기본 생성자를 통해 TIMEOUT이 적용된 SseEmitter를 생성한다.
     * 테스트에서는 mock SseEmitter를 주입하여 초기 이벤트 전송 실패 같은 예외 경로를 검증할 수 있다.
     */
    private final Supplier<SseEmitter> emitterSupplier;

    /**
     * 운영용 기본 생성자
     * - 30분 타임아웃이 적용된 SseEmitter를 생성한다.
     */
    public SseConnectionManager() {
        this(() -> new SseEmitter(TIMEOUT));
    }

    /**
     * 테스트용 생성자
     * - SseEmitter 생성을 외부에서 주입받아 전송 실패 경로를 검증할 수 있게 한다.
     */
    SseConnectionManager(Supplier<SseEmitter> emitterSupplier) {
        this.emitterSupplier = emitterSupplier;
    }

    /**
     * SSE 연결 생성
     *
     * 처리 흐름:
     * - SseEmitter를 생성하고 사용자별 연결 Map에 등록한다.
     * - 동일 사용자가 재연결하면 기존 emitter를 complete 처리하고 새 emitter로 교체한다.
     * - 연결 직후 초기 이벤트(connect)를 전송하여 클라이언트에 연결 성공을 알린다.
     * - 타임아웃, 에러, 완료 콜백 발생 시 현재 emitter를 Map에서 자동 제거한다.
     *
     * 예외 처리:
     * - 초기 connect 이벤트 전송 실패 시 해당 emitter를 Map에서 제거하고 completeWithError 처리한다.
     */
    public SseEmitter connect(Long userId) {
        SseEmitter emitter = emitterSupplier.get();

        emitter.onCompletion(() -> {
            log.debug("SSE 연결 완료 - userId: {}", userId);
            emitters.remove(userId, emitter);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE 연결 타임아웃 - userId: {}", userId);
            emitters.remove(userId, emitter);
        });

        emitter.onError(e -> {
            log.warn("SSE 연결 에러 - userId: {}", userId, e);
            emitters.remove(userId, emitter);
        });

        SseEmitter previousEmitter = emitters.put(userId, emitter);

        if (previousEmitter != null) {
            previousEmitter.complete();
            log.debug("기존 SSE 연결 교체 - userId: {}", userId);
        }

        sendInitialEvent(userId, emitter);

        log.info("SSE 연결 성공 - userId: {}, 현재 연결 수: {}", userId, emitters.size());

        return emitter;
    }

    /**
     * 특정 사용자에게 SSE 알림 전송
     * - 연결이 없는 사용자에게는 전송을 시도하지 않는다
     * - 전송 실패 시 해당 emitter를 제거하고 로그를 기록한다
     */
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
        } catch (Exception e) {
            log.warn("SSE 알림 전송 실패 - userId: {}", userId, e);
            emitters.remove(userId, emitter);
            emitter.completeWithError(e);
        }
    }

    /**
     * SSE 연결 해제
     * - emitter를 제거하고 complete 처리한다
     */
    public void disconnect(Long userId) {
        SseEmitter emitter = emitters.remove(userId);

        if (emitter != null) {
            emitter.complete();
            log.debug("SSE 연결 해제 - userId: {}", userId);
        }
    }

    /**
     * 연결 직후 초기 이벤트 전송
     *
     * 목적:
     * - SSE 연결 직후 클라이언트에 connect 이벤트를 보내 연결 성공을 명시한다.
     * - 일부 환경에서 연결 직후 데이터가 없으면 타임아웃 또는 연결 불안정이 발생할 수 있어 초기 이벤트를 전송한다.
     *
     * 실패 처리:
     * - 초기 이벤트 전송에 실패하면 해당 emitter는 정상 연결로 볼 수 없다.
     * - 따라서 emitters Map에서 제거하고 completeWithError로 종료한다.
     */
    private void sendInitialEvent(Long userId, SseEmitter emitter) {
        try {
            emitter.send(
                SseEmitter.event()
                    .name("connect")
                    .data("SSE 연결 성공")
            );
        } catch (Exception e) {
            log.warn("SSE 초기 이벤트 전송 실패 - userId: {}", userId, e);
            emitters.remove(userId, emitter);
            emitter.completeWithError(e);
        }
    }

}
