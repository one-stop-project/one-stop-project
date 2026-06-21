package com.sparta.one_stop.domain.auth.service;

import com.sparta.one_stop.domain.auth.dto.request.LoginRequest;
import com.sparta.one_stop.domain.admin.security.SuspensionPolicyService;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.domain.user.service.UserStatusCacheService;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuthService 조회 + 인증 검증 책임 분리 서비스
 *
 * ═══════════════════════════════════════════════════════════
 *  분리 이유 — Spring AOP self-invocation 해결
 * ═══════════════════════════════════════════════════════════
 *
 *  문제:
 *    AuthService 내부에서 @Transactional 메서드를 호출하면
 *    프록시를 거치지 않아 트랜잭션이 적용되지 않음
 *
 *  해결:
 *    조회/검증 책임을 별도 Service로 분리
 *    AuthService는 이 클래스를 주입받아 호출 (프록시 경유)
 *
 * ═══════════════════════════════════════════════════════════
 *  책임
 * ═══════════════════════════════════════════════════════════
 *
 *  1. 사용자 인증 (이메일 + 비밀번호)
 *  2. 활성 상태 검증
 *  3. 마지막 로그인 시간 갱신
 *  4. User 조회 (refresh 시 1회로 통합)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthQueryService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserStatusCacheService userStatusCacheService;
    private final SuspensionPolicyService suspensionPolicyService;

    /**
     * 사용자 인증 — BCrypt 검증 + 활성 상태 확인
     *
     * 트랜잭션:
     *   - readOnly = true (조회 + Hibernate 캐시 최적화)
     *
     * 타이밍 공격 방어:
     *   - 계정 없을 때도 dummyHash로 BCrypt 시간 동일하게 유지
     *
     * @return 인증 성공한 User (활성 상태 보장)
     * @throws CustomException AUTH_004 (이메일 or 비밀번호 불일치)
     * @throws CustomException AUTH_005 (정지) / AUTH_006 (탈퇴)
     */
    @Transactional
    public User authenticate(LoginRequest request, String dummyHash) {
        User user = userRepository.findByEmail(request.email()).orElse(null);

        if (user == null) {
            // 타이밍 공격 방어 — BCrypt 시간 일관성 유지
            passwordEncoder.matches(request.password(), dummyHash);
            log.warn("로그인 실패 - 계정 없음");
            throw new CustomException(ErrorCode.AUTH_004);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("로그인 실패 - 비밀번호 불일치: userId={}", user.getId());
            throw new CustomException(ErrorCode.AUTH_004);
        }

        suspensionPolicyService.validateOrRelease(user);
        // Entity가 자기 자신을 검증 (DDD)
        user.verifyActive();

        return user;
    }

    /**
     * Refresh 시 사용자 조회 + 활성 검증 + Role 반환
     *
     * v1 대비 개선:
     *   - findById 2회 호출 → 1회로 통합 (refresh 시 DB 부하 50% 감소)
     */
    @Transactional
    public User findActiveUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));
        suspensionPolicyService.validateOrRelease(user);
        user.verifyActive();
        return user;
    }

    /**
     * 마지막 로그인 시간 갱신 (best-effort)
     *
     * 실패해도 로그인 자체엔 영향 없음
     * 단, 트랜잭션 분리되어 있어 더티 체킹 작동 보장
     */
    @Transactional
    public void recordLogin(Long userId) {
        try {
            userRepository.findById(userId).ifPresent(User::recordLogin);
        } catch (Exception e) {
            log.warn("lastLoginAt 갱신 실패 (무시): userId={}", userId, e);
        }
    }

    // 활성 상태만 검증
    // 사용시점 : 1. JwtAuthenticationFilter, 2. User 전체 필요 없는 경우
    // 효과 : DB조회X (캐시 히트 시)
    public void verifyActiveByCache(Long userId) {
        userStatusCacheService.verifyActive(userId);
    }


    // 토큰 버전 검증 - 발급 시점 ver가 현재 ver보다 낮으면 무효화된 토큰
    // 비번 변경/탈퇴/정지 시 version++되므로, 그 이전 발급된 AT는 tokenVersion < 현재 version이 되어 거부
    public void verifyTokenVersion(Long userId, int tokenVersion) {
        int currentVersion = userStatusCacheService.getTokenVersion(userId);
        if (tokenVersion < currentVersion) {
            throw new com.sparta.one_stop.global.exception.CustomException(
                com.sparta.one_stop.global.exception.ErrorCode.AUTH_009,
                "보안 정책에 의해 만료된 토큰입니다. 다시 로그인해주세요.");
        }
    }
}
