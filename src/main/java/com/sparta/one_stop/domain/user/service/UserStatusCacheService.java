package com.sparta.one_stop.domain.user.service;


// User 상태 캐싱 전담 서비스
// Redis에 status만 5분 캐싱
// 정지/탈퇴 시 즉시 evict
// DB부하 감소

// Spring AOP self-invocation 회피를 위해 별도 서비스 작성
// 다른 도메인도 이 서비스를 주입받을 수 있음

import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class UserStatusCacheService {

    private final UserRepository userRepository;


    // User 상태 조회(캐시 우선)
    @Cacheable(value = "userStatus", key = "#userId", cacheManager = "redisCacheManager")
    @Transactional(readOnly = true)
    public UserStatus getStatus(Long userId) {
        log.debug("[UserStatusCache] DB조회 (캐시미스) : userId = {}", userId);

        return userRepository.findStatusById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  토큰 버전 — user-level 무효화 (DB 영속 계층)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 현재 토큰 버전 조회 (캐시 우선) — 매 요청 필터에서 호출되므로 캐싱 필수
     *
     * <p>status 캐시와 별도 키로 둬 인증 핵심 경로(verifyActiveByCache)에
     * 영향을 주지 않는다.
     */
    @Cacheable(value = "userTokenVersion", key = "#userId", cacheManager = "redisCacheManager")
    @Transactional(readOnly = true)
    public int getTokenVersion(Long userId) {
        log.debug("[UserTokenVersion] DB조회 (캐시미스) : userId = {}", userId);
        return userRepository.findTokenVersionById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));
    }

    /** 토큰 버전 캐시 무효화 — version++ 후 호출 */
    @CacheEvict(value = "userTokenVersion", key = "#userId", cacheManager = "redisCacheManager")
    public void evictTokenVersion(Long userId) {
        log.info("[UserTokenVersion] 캐시 무효화: userId={}", userId);
    }


    // 캐시 무효화 - 상태 변경 후 호출
    // 호출 시점
    //   - user.suspend() 후
    //   - user.withdraw() 후
    //   - user.reactivate() 후
    //   - 비밀번호 변경 (보안 강화 시)
    @CacheEvict(value = "userStatus", key = "#userId", cacheManager = "redisCacheManager")
    public void evict(Long userId) {
        log.info("[UserStatusCache] 캐시 무효화: userId={}", userId);
    }

    // 캐시 갱신 - 상태 변경 후 새 값으로 즉시 갱신
    //   - 갱신 직후 조회 요청에서 cache miss 방지
    //   - 트래픽 많은 사용자에 유리

    @CachePut(value = "userStatus", key = "#userId", cacheManager = "redisCacheManager")
    public UserStatus evictAndRefresh(Long userId, UserStatus newStatus) {
        log.info("[UserStatusCache] 캐시 갱신 : userId={}, newStatus={}", userId, newStatus);

        return newStatus;
    }


    // 활성 상태 검증 (캐시 활용)
    // AuthQueryService.findActiveUser 대체 후보:
    //- User 전체 객체 필요 없으면 이 메서드 사용
    //   - User 전체 필요하면 findById + verifyActive
    public void verifyActive(Long userId) {
        UserStatus status = getStatus(userId);

        if (status == UserStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.AUTH_005);
        }
        if (status == UserStatus.WITHDRAWN) {
            throw new CustomException(ErrorCode.AUTH_006);
        }
    }
}
