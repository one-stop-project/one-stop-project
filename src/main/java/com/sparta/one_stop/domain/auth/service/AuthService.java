package com.sparta.one_stop.domain.auth.service;

import com.sparta.one_stop.domain.auth.dto.request.LoginRequest;
import com.sparta.one_stop.domain.auth.dto.request.SignUpRequest;
import com.sparta.one_stop.domain.auth.dto.request.TokenRefreshRequest;
import com.sparta.one_stop.domain.auth.dto.response.LoginResponse;
import com.sparta.one_stop.domain.auth.dto.response.SignUpResponse;
import com.sparta.one_stop.domain.auth.dto.response.TokenRefreshResponse;
import com.sparta.one_stop.domain.auth.dto.result.LoginResult;
import com.sparta.one_stop.domain.auth.dto.result.RefreshResult;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.ratelimit.RateLimitPolicy;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.ratelimit.RateLimitService;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * AuthService — 인증 흐름 조율 (얇은 Facade)
 *
 * ═══════════════════════════════════════════════════════════
 *  설계 원칙
 * ═══════════════════════════════════════════════════════════
 *
 *  1. 책임 분리
 *     - AuthService:        흐름 조율 (트랜잭션 없음)
 *     - AuthQueryService:   DB 조회 + 인증 검증 (@Transactional)
 *     - AuthCommandService: 회원가입 저장 (@Transactional)
 *     - RedisTokenService:  RT/블랙리스트 관리
 *     - DeviceLimitService: 다중 기기 관리
 *
 *  2. self-invocation 회피
 *     - 모든 @Transactional 호출이 다른 Bean을 경유
 *     - 프록시 패턴 정상 작동 보장
 *
 *  3. Rate Limit 일관 적용
 *     - 모든 인증 진입점에 Rate Limit 체크
 *     - BCrypt CPU 폭발 방어
 *
 * ═══════════════════════════════════════════════════════════
 *  v1 대비 핵심 변경
 * ═══════════════════════════════════════════════════════════
 *
 *  1. self-invocation 완전 제거
 *     - authenticateUser, saveUserAndSeller, validateActiveUser → 별도 Service
 *
 *  2. login 흐름의 Rate Limit 3계층 적용
 *     - 글로벌 → IP → 계정 순서로 점진적 차단
 *
 *  3. refresh의 DB 조회 통합
 *     - validateActiveUser + getUserRole → findActiveUser 1회로
 *
 *  4. 로그아웃 부분 실패 처리 명확화
 *     - RT 삭제 / 기기 제거 / 블랙리스트 등록 각각 독립적으로 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthQueryService authQueryService;
    private final AuthCommandService authCommandService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTokenService redisTokenService;
    private final DeviceLimitService deviceLimitService;
    private final RateLimitService rateLimitService;

    private static final Set<UserRole> ALLOWED_SIGNUP_ROLES = Set.of(UserRole.BUYER, UserRole.SELLER);

    private String dummyHash;

    @PostConstruct
    public void init() {
        // 타이밍 공격 방어용 더미 해시 (애플리케이션 시작 시 1회 생성)
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
        log.info("[AuthService] 초기화 완료 — dummyHash 생성됨");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  POST /api/auth/signup
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    public SignUpResponse signup(SignUpRequest request, String clientIp) {
        // 1. Rate Limit (어뷰징 계정 생성 방어)
        rateLimitService.tryConsume(RateLimitPolicy.SIGNUP_PER_IP, clientIp);

        // 2. 기본 검증 (DB 조회 전 빠른 검증)
        if (!ALLOWED_SIGNUP_ROLES.contains(request.role())) {
            throw new CustomException(ErrorCode.AUTH_011, "허용되지 않은 가입 권한입니다.");
        }
        if (request.role() == UserRole.SELLER) {
            validateSellerFields(request);
        }

        // 3. 이메일 중복 사전 체크 (UX — 친절한 에러 메시지)
        //    동시성 안전성은 UNIQUE 인덱스 + AuthCommandService의 saveAndFlush가 보장
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.AUTH_002);
        }

        // 4. 가입 처리 (별도 Service — 트랜잭션 격리)
        User savedUser = authCommandService.signup(request);
        return SignUpResponse.from(savedUser);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  POST /api/auth/login — 가장 트래픽 부하 큰 흐름
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    public LoginResult login(LoginRequest request, String deviceId, String clientIp) {
        // 1. Rate Limit 3계층 (BCrypt 도달 전 차단)
        rateLimitService.tryConsume(RateLimitPolicy.LOGIN_PER_GLOBAL, "all");
        rateLimitService.tryConsume(RateLimitPolicy.LOGIN_PER_IP, clientIp);
        rateLimitService.tryConsume(RateLimitPolicy.LOGIN_PER_ACCOUNT, request.email());

        // 2. 사용자 인증 (AuthQueryService — 트랜잭션 프록시 경유)
        User user = authQueryService.authenticate(request, dummyHash);

        // 3. 토큰 발급 (트랜잭션 외부 — DB 부담 없음)
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), deviceId);

        // 4. 기기 등록 (Lua Script 원자 실행)
        String evictedDeviceId = deviceLimitService.registerDevice(user.getId(), deviceId);
        if (evictedDeviceId != null) {
            log.info("[기기 제한] 기존 기기 자동 로그아웃: userId={}, evicted={}",
                user.getId(), evictedDeviceId);
            // TODO: 알림 도메인 연동 시 — 기존 기기 사용자에게 푸시 발송
        }

        // 5. RT 저장
        redisTokenService.saveRefreshToken(
            user.getId(), deviceId, refreshToken,
            jwtTokenProvider.getRefreshTokenExpirySeconds()
        );

        // 6. 마지막 로그인 시간 갱신 (별도 트랜잭션, best-effort)
        authQueryService.recordLogin(user.getId());

        LoginResponse response = LoginResponse.of(
            accessToken,
            jwtTokenProvider.getAccessTokenExpirySeconds(),
            user
        );
        return new LoginResult(response, refreshToken);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  POST /api/auth/refresh
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    public RefreshResult refresh(TokenRefreshRequest request, String deviceId) {
        // 1. Rate Limit (refresh 폭주 방어)
        rateLimitService.tryConsume(RateLimitPolicy.REFRESH_PER_DEVICE, deviceId);

        String oldRefreshToken = request.refreshToken();

        // 2. 토큰 파싱 + 검증
        Claims claims;
        try {
            claims = jwtTokenProvider.parseClaims(oldRefreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.AUTH_010);
        }

        // 3. deviceId 이중 검증 (페이로드 ↔ 쿠키)
        String tokenDeviceId = claims.get("deviceId", String.class);
        if (tokenDeviceId == null || !tokenDeviceId.equals(deviceId)) {
            log.warn("RT-Cookie deviceId 불일치 (탈취 의심): tokenDeviceId={}, cookieDeviceId={}",
                tokenDeviceId, deviceId);
            throw new CustomException(ErrorCode.AUTH_010);
        }

        // 4. 사용자 조회 + 활성 검증 (1회 조회로 통합)
        Long userId = jwtTokenProvider.getUserId(claims);
        User user = authQueryService.findActiveUser(userId);

        // 5. 새 토큰 발급
        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId(), deviceId);

        // 6. Lua Script CAS — 원자적 RTR 갱신
        boolean isRotated = redisTokenService.rotateRefreshTokenCAS(
            userId, deviceId, oldRefreshToken, newRefreshToken,
            jwtTokenProvider.getRefreshTokenExpirySeconds()
        );

        if (!isRotated) {
            log.warn("RT 원자적 갱신 실패 (동시성 충돌/탈취 의심): userId={}, deviceId={}",
                userId, deviceId);
            throw new CustomException(ErrorCode.AUTH_010);
        }

        // 7. 기기 활동 시간 갱신 (LRU 신선도)
        deviceLimitService.touchDevice(userId, deviceId);

        TokenRefreshResponse response = TokenRefreshResponse.of(
            newAccessToken,
            jwtTokenProvider.getAccessTokenExpirySeconds()
        );
        return new RefreshResult(response, newRefreshToken);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  POST /api/auth/logout
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    public void logout(Long userId, String deviceId, String accessToken) {
        // 각 작업이 독립적으로 실패해도 다른 작업은 진행 (best-effort)

        // 1. RT 삭제 (특정 기기만)
        try {
            redisTokenService.deleteRefreshToken(userId, deviceId);
        } catch (Exception e) {
            log.error("로그아웃 부분 실패 — RT 삭제: userId={}", userId, e);
        }

        // 2. 기기 목록에서 제거
        try {
            deviceLimitService.removeDevice(userId, deviceId);
        } catch (Exception e) {
            log.error("로그아웃 부분 실패 — 기기 제거: userId={}", userId, e);
        }

        // 3. AT 블랙리스트 등록
        if (accessToken != null) {
            try {
                String jti = jwtTokenProvider.getJti(accessToken);
                long expiration = jwtTokenProvider.getExpirationSeconds(accessToken);
                if (expiration > 0) {
                    redisTokenService.addToBlacklist(jti, expiration);
                } else {
                    log.debug("이미 만료된 토큰은 블랙리스트 추가 생략: jti={}", jti);
                }
            } catch (Exception e) {
                log.error("로그아웃 부분 실패 — JTI 블랙리스트: userId={}", userId, e);
            }
        }

        log.info("로그아웃 완료: userId={}, deviceId={}", userId, deviceId);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Private
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void validateSellerFields(SignUpRequest request) {
        if (request.shopName() == null || request.shopName().isBlank()) {
            throw new CustomException(ErrorCode.COMMON_001);
        }
        if (request.businessNumber() == null || request.businessNumber().isBlank()) {
            throw new CustomException(ErrorCode.COMMON_001);
        }
    }
}
