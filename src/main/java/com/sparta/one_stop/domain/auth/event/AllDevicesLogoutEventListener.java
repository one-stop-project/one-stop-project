package com.sparta.one_stop.domain.auth.event;

import com.sparta.one_stop.domain.auth.service.DeviceLimitService;
import com.sparta.one_stop.domain.auth.service.RedisTokenService;
import com.sparta.one_stop.domain.user.service.UserStatusCacheService;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 사용자의 모든 인증 세션을 무효화하는 보안 이벤트 Listener.
 *
 * <p>{@code AFTER_COMMIT}에서 동기 실행한다.</p>
 * <ul>
 *   <li>DB tokenVersion 증가가 커밋된 뒤 캐시를 제거한다.</li>
 *   <li>비동기 실행 지연 동안 이전 tokenVersion 캐시가 사용되는 race를 차단한다.</li>
 *   <li>기존 Access Token cutoff와 모든 Refresh Token 제거도 응답 전에 완료한다.</li>
 * </ul>
 *
 * <p>비밀번호 변경, 회원 탈퇴, 정지, 침해 대응은 빈도가 낮고 보안 우선 작업이므로
 * 짧은 응답 시간보다 즉시 무효화 보장을 우선한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AllDevicesLogoutEventListener {

    private final DeviceLimitService deviceLimitService;
    private final RedisTokenService redisTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserStatusCacheService userStatusCacheService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AllDevicesLogoutEvent event) {
        Long userId = event.userId();

        /*
         * 가장 먼저 tokenVersion 캐시를 제거한다.
         * AFTER_COMMIT이므로 이후 캐시 미스는 DB의 최신 tokenVersion을 읽는다.
         */
        evictTokenVersion(userId, event.reason());

        /*
         * tokenVersion 검증과 별개로 사용자 단위 cutoff를 즉시 기록해
         * 기존 Access Token을 한 번 더 차단한다.
         */
        invalidateAccessTokens(userId, event.reason());

        /*
         * 기기 ZSET과 모든 Refresh Token을 원자적으로 제거한다.
         * 실패하더라도 앞의 tokenVersion/cutoff 방어선은 이미 적용된 상태다.
         */
        removeAllDeviceSessions(userId, event.reason());
    }

    private void evictTokenVersion(Long userId, String reason) {
        try {
            userStatusCacheService.evictTokenVersion(userId);
            log.info(
                "[ALL_DEVICE_LOGOUT] tokenVersion 캐시 동기 무효화 완료: userId={}, reason={}",
                userId,
                reason
            );
        } catch (Exception e) {
            // 보안상 중요한 실패이므로 삼키되 반드시 ERROR로 남긴다.
            // 다음 cutoff 작업은 계속 진행해 다중 방어선을 유지한다.
            log.error(
                "[ALL_DEVICE_LOGOUT] tokenVersion 캐시 무효화 실패: userId={}, reason={}",
                userId,
                reason,
                e
            );
        }
    }

    private void invalidateAccessTokens(Long userId, String reason) {
        try {
            redisTokenService.invalidateUserTokens(
                userId,
                jwtTokenProvider.getAccessTokenExpirySeconds()
            );
            log.info(
                "[ALL_DEVICE_LOGOUT] Access Token cutoff 등록 완료: userId={}, reason={}",
                userId,
                reason
            );
        } catch (Exception e) {
            log.error(
                "[ALL_DEVICE_LOGOUT] Access Token cutoff 등록 실패: userId={}, reason={}",
                userId,
                reason,
                e
            );
        }
    }

    private void removeAllDeviceSessions(Long userId, String reason) {
        try {
            long deletedCount = deviceLimitService.removeAllDevices(userId);
            log.info(
                "[ALL_DEVICE_LOGOUT] 전체 기기 세션 제거 완료: userId={}, reason={}, deletedCount={}",
                userId,
                reason,
                deletedCount
            );
        } catch (Exception e) {
            log.error(
                "[ALL_DEVICE_LOGOUT] 전체 기기 세션 제거 실패: userId={}, reason={}",
                userId,
                reason,
                e
            );
        }
    }
}
