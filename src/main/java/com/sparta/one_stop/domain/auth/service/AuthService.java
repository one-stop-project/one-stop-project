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
import com.sparta.one_stop.global.audit.SecurityAuditEvent;
import com.sparta.one_stop.global.audit.SecurityAuditEventType;
import com.sparta.one_stop.global.audit.SecurityAuditService;
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
 *  핵심 기능 설명
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

    private static final Set<UserRole> ALLOWED_SIGNUP_ROLES = Set.of(UserRole.BUYER, UserRole.SELLER);
    private final AuthQueryService authQueryService;
    private final AuthCommandService authCommandService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTokenService redisTokenService;
    private final DeviceLimitService deviceLimitService;
    private final RateLimitService rateLimitService;
    private final SecurityAuditService securityAuditService;
    private final DeviceContextService deviceContextService;
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
    public LoginResult login(LoginRequest request, String deviceId, String userAgent, String clientIp) {
        // 1. Rate Limit 계층 (BCrypt 도달 전 차단)
        rateLimitService.tryConsume(RateLimitPolicy.LOGIN_PER_GLOBAL, "all");
        rateLimitService.tryConsume(RateLimitPolicy.LOGIN_PER_IP, clientIp);
        rateLimitService.tryConsume(RateLimitPolicy.LOGIN_PER_ACCOUNT, request.email());

        // 2. 사용자 인증 (AuthQueryService — 트랜잭션 프록시 경유)
        User user = authQueryService.authenticate(request, dummyHash);

        rateLimitService.tryConsume(RateLimitPolicy.LOGIN_CONCURRENT_PER_ACCOUNT, request.email());
        rateLimitService.tryConsume(RateLimitPolicy.DEVICE_REGISTER_PER_IP, clientIp);

        boolean isNewDevice = deviceLimitService.isNewDevice(user.getId(), deviceId);

        // 3. 토큰 발급 (트랜잭션 외부 — DB 부담 없음)
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), deviceId);

        // 4. 기기 등록 (Lua Script 원자 실행) — 결과 객체 활용
        DeviceLimitService.DeviceRegistrationResult result =
            deviceLimitService.registerDevice(user.getId(), deviceId);

        if (result.isNewDevice()) {
            rateLimitService.tryConsume(RateLimitPolicy.DEVICE_REGISTER_PER_ACCOUNT, String.valueOf(user.getId()));
        }

        // 5. RT 저장
        redisTokenService.saveRefreshToken(
            user.getId(), deviceId, refreshToken,
            jwtTokenProvider.getRefreshTokenExpirySeconds()
        );

        deviceContextService.bindContext(user.getId(), deviceId, userAgent, clientIp);

        // 새 기기 감지 시 보안 이벤트 기록
        if (result.isNewDevice()) {
            securityAuditService.record(SecurityAuditEvent.builder()
                .eventType(SecurityAuditEventType.LOGIN_SUCCESS)
                .actorUserId(user.getId())
                .actorEmail(user.getEmail())
                .result("SUCCESS")
                .metadata(String.format(
                    "{\"newDevice\":true,\"deviceId\":\"%s\",\"currentDeviceCount\":%d}",
                    deviceId, result.currentSize()))
                .build());
        }

        // LRU 추방 발생 시 보안 이벤트 (다른 기기 강제 로그아웃)
        if (result.evictedDeviceId() != null) {
            securityAuditService.record(SecurityAuditEvent.builder()
                .eventType(SecurityAuditEventType.DEVICE_LIMIT_EXCEEDED)
                .actorUserId(user.getId())
                .actorEmail(user.getEmail())
                .result("EVICTED")
                .metadata(String.format(
                    "{\"evictedDeviceId\":\"%s\",\"newDeviceId\":\"%s\"}",
                    result.evictedDeviceId(), deviceId))
                .build());

            // 추방된 기기의 RT 강제 삭제 (이미 deviceLimitService에서 ZSET 추방되어도
            // RT는 살아있으므로 명시 삭제)
            redisTokenService.deleteRefreshToken(user.getId(), result.evictedDeviceId());
        }

        // Fail-Open 발생 시 보안 이벤트 (운영팀 인지 필요)
        if (result.failOpen()) {
            log.warn("[AUTH] Fail-Open 로그인 (Redis 장애): userId={}", user.getId());
            securityAuditService.record(SecurityAuditEvent.builder()
                .eventType(SecurityAuditEventType.SUSPICIOUS_PATTERN_DETECTED)
                .actorUserId(user.getId())
                .result("FAIL_OPEN")
                .errorMessage("Redis 장애로 기기 추적 일시 중단")
                .build());
        }

        // 6. 마지막 로그인 시간 갱신 (별도 트랜잭션, best-effort)
        authQueryService.recordLogin(user.getId());

        return new LoginResult(
            LoginResponse.of(accessToken, jwtTokenProvider.getAccessTokenExpirySeconds(), user),
            refreshToken
        );
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  POST /api/auth/refresh
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    public RefreshResult refresh(TokenRefreshRequest request, String deviceId,String userAgent, String clientIp)
    {
        // 1. Rate Limit (refresh 폭주 방어)
        rateLimitService.tryConsume(RateLimitPolicy.REFRESH_PER_DEVICE, deviceId);

        String oldRefreshToken = request.refreshToken();

        // 2. 토큰 파싱 + 검증
        Claims claims = jwtTokenProvider.parseClaims(oldRefreshToken);
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
            securityAuditService.record(SecurityAuditEvent.builder()
                .eventType(SecurityAuditEventType.TOKEN_DEVICE_MISMATCH)
                .result("BLOCKED")
                .errorMessage(String.format(
                    "deviceId 불일치: token=%s, cookie=%s", tokenDeviceId, deviceId))
                .build());
            throw new CustomException(ErrorCode.AUTH_006);
        }

        // 4. 사용자 조회 + 활성 검증
        Long userId = jwtTokenProvider.getUserId(claims);

        // ZSET 미등록 기기 차단: isNewDevice == true 면 "등록 안 된 기기"
        // (refresh는 이미 등록된 기기에서만 정상. 미등록이면 탈취/추방 의심)
        if (deviceLimitService.isNewDevice(userId, deviceId)) {
            log.warn("refresh 시도된 기기가 ZSET에 없음 (이상 행위): userId={}, deviceId={}",
                userId, deviceId);

            securityAuditService.record(SecurityAuditEvent.builder()
                .eventType(SecurityAuditEventType.SUSPICIOUS_PATTERN_DETECTED)
                .actorUserId(userId)
                .result("BLOCKED")
                .errorMessage("refresh 요청 기기가 등록되지 않음")
                .metadata(String.format("{\"deviceId\":\"%s\"}", deviceId))
                .build());

            throw new CustomException(ErrorCode.AUTH_006, "등록되지 않은 기기");
        }

        User user = authQueryService.findActiveUser(userId);

        // 5. 새 토큰 발급
        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId(), deviceId);

        // 6. Lua Script CAS — 원자적 RTR 갱신
        boolean rotated = redisTokenService.rotateRefreshTokenCAS(
            userId, deviceId, oldRefreshToken, newRefreshToken,
            jwtTokenProvider.getRefreshTokenExpirySeconds()
        );

        if (!rotated) {
            log.warn("RT 원자적 갱신 실패 (동시성 충돌/탈취 의심): userId={}, deviceId={}",
                userId, deviceId);

            securityAuditService.record(SecurityAuditEvent.builder()
                .eventType(SecurityAuditEventType.TOKEN_REFRESH_FAILED)
                .actorUserId(userId)
                .result("FAILURE")
                .errorMessage("CAS 회전 실패 (동시성 충돌 또는 탈취 의심)")
                .build());

            throw new CustomException(ErrorCode.AUTH_007);
        }

        // 6-1. 기기 컨텍스트 검증 (읽기 전용) — 접속 환경 변화 감지
        DeviceContextService.ContextVerifyResult ctxResult =
            deviceContextService.verifyContext(userId, deviceId, userAgent, clientIp);
        if (ctxResult == DeviceContextService.ContextVerifyResult.MISMATCH) {
            // 환경 변동 — 탈취 의심. 모바일 false positive 고려해 기록+통과 정책.
            log.warn("[AUTH] 컨텍스트 불일치 (탈취 의심, 통과+기록): userId={}, deviceId={}",
                userId, deviceId);
            securityAuditService.record(SecurityAuditEvent.builder()
                .eventType(SecurityAuditEventType.SUSPICIOUS_PATTERN_DETECTED)
                .actorUserId(userId)
                .result("CONTEXT_MISMATCH")
                .errorMessage("접속 환경(OS/브라우저/IP대역) 변동 감지")
                .build());
        }

        // 7. 기기 활동 시간 갱신 (LRU 신선도)
        deviceLimitService.touchDevice(userId, deviceId);

        // 7-1. 컨텍스트 갱신 — 정상 환경 변화(OS 업데이트 등) 반영
        deviceContextService.bindContext(userId, deviceId, userAgent, clientIp);

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

        // deviceId가 있을 때만 기기 단위 정리 (null이면 ...:userId:null 키 삭제 시도 방지)
        if (deviceId != null && !deviceId.isBlank()) {
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

            // 2-1. 기기 컨텍스트 제거
            try {
                deviceContextService.removeContext(userId, deviceId);
            } catch (Exception e) {
                log.error("로그아웃 부분 실패 — 컨텍스트 제거: userId={}", userId, e);
            }
        } else {
            log.debug("logout — deviceId 없음, 기기 단위 정리 생략: userId={}", userId);
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
            throw new CustomException(ErrorCode.SELLER_010);
        }
        if (request.businessNumber() == null || request.businessNumber().isBlank()) {
            throw new CustomException(ErrorCode.SELLER_011);
        }
    }
}

