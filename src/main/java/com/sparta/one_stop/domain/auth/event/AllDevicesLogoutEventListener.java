package com.sparta.one_stop.domain.auth.event;

import com.sparta.one_stop.domain.auth.service.DeviceLimitService;
import com.sparta.one_stop.domain.auth.service.RedisTokenService;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 전체 기기 로그아웃 이벤트 Listener
 *
 * 핵심 설계:
 *   - @TransactionalEventListener(AFTER_COMMIT)
 *     → DB 트랜잭션 커밋 성공 후에만 실행
 *     → DB 롤백 시 Redis 정리도 안 함 (일관성 보장)
 *
 *   - @Async
 *     → API 응답 시간에 영향 없음 (Fire-and-Forget)
 *     → Redis 정리는 사용자가 기다릴 필요 없음
 *
 * 장애 격리:
 *   - 이 Listener에서 예외 발생해도 원본 트랜잭션엔 영향 없음
 *   - DeviceLimitService 내부에서 이미 Fail-Open 처리
 *
 * 확장 포인트:
 *   - 향후 알림 도메인 추가 시 같은 이벤트로 푸시 발송
 *   - 보안 감사 로그 적재
 *   - 이상 행위 탐지 트리거
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AllDevicesLogoutEventListener {

    private final DeviceLimitService deviceLimitService;
    private final RedisTokenService redisTokenService;
    private final JwtTokenProvider jwtTokenProvider;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AllDevicesLogoutEvent event) {
        try {
            // ZSET + 모든 RT 일괄 삭제 (Lua Script로 원자 실행)
            long deletedCount = deviceLimitService.removeAllDevices(event.userId());

            // user-level AT 무효화 — cutoff 이전 발급된 모든 AT 거부 (기존 AT 무력화)
            //    RT만 지우면 살아있는 AT가 15분간 유효하므로, AT cutoff도 함께 등록
            redisTokenService.invalidateUserTokens(
                event.userId(), jwtTokenProvider.getAccessTokenExpirySeconds());

            log.info("[AllDevicesLogoutEvent] 처리 완료: userId={}, reason={}, deletedCount={}",
                event.userId(), event.reason(), deletedCount);

            // TODO: 알림 도메인 연동 시
            // notificationService.send(event.userId(), buildMessage(event.reason()));

        } catch (Exception e) {
            // Listener 예외는 원본 트랜잭션에 영향 안 줌
            // 단, 보안 이벤트이므로 반드시 로깅
            log.error("[AllDevicesLogoutEvent] 처리 실패: userId={}, reason={}",
                event.userId(), event.reason(), e);
        }
    }
}
