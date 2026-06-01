package com.sparta.one_stop.global.ai.fallback;

/**
 * AI 호출 실패 시 공통 fallback 처리 인터페이스.
 * 각 AI 서비스의 fallback 메서드에서 이 인터페이스를 구현한 핸들러에 위임합니다.
 *
 * @param <T> fallback 결과 타입
 */
@FunctionalInterface
public interface AiFallbackHandler<T> {
    T handle(Throwable throwable);
}
