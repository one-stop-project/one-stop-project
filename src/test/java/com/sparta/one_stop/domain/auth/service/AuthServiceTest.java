package com.sparta.one_stop.domain.auth.service;

import com.sparta.one_stop.domain.auth.dto.request.LoginRequest;
import com.sparta.one_stop.domain.auth.dto.request.SignUpRequest;
import com.sparta.one_stop.domain.auth.dto.request.TokenRefreshRequest;
import com.sparta.one_stop.domain.auth.dto.result.LoginResult;
import com.sparta.one_stop.domain.auth.dto.result.RefreshResult;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.audit.SecurityAuditService;
import com.sparta.one_stop.global.enums.ratelimit.RateLimitPolicy;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.ratelimit.RateLimitService;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AuthService (퍼사드) 테스트
 *
 * <p><b>검증 목적</b>
 * <ul>
 *   <li>로그인 흐름 — Rate Limit → 인증 → 토큰 발급 → 기기 등록 → RT 저장 → 로그인 기록 순서 보장</li>
 *   <li>토큰 재발급 — deviceId 이중 검증, RTR + CAS 원자적 갱신</li>
 *   <li>로그아웃 — 부분 실패(Best-effort) 정책 — 한 단계 실패해도 다음 단계 진행</li>
 *   <li>회원가입 — Rate Limit 통과 후 Command 서비스 위임</li>
 * </ul>
 *
 * <p><b>전략</b>: 외부 의존성은 모두 Mock. 흐름과 협업 객체 호출 순서를 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService (퍼사드) 테스트")
class AuthServiceTest {

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "Password1!";
    private static final String DEVICE_ID = "device-abc-123";
    private static final String USER_AGENT = "Mozilla/5.0 (Test) Chrome/120.0";
    private static final String CLIENT_IP = "127.0.0.1";
    private static final String DUMMY_HASH = "$2a$10$dummy.hash.value";
    private static final String ACCESS_TOKEN = "access-token-xyz";
    private static final String REFRESH_TOKEN = "refresh-token-xyz";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token-xyz";
    @InjectMocks
    private AuthService authService;
    @Mock private AuthQueryService authQueryService;
    @Mock private AuthCommandService authCommandService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RedisTokenService redisTokenService;
    @Mock private DeviceLimitService deviceLimitService;
    @Mock private RateLimitService rateLimitService;
    @Mock private UserRepository userRepository;
    @Mock private SecurityAuditService securityAuditService;
    @Mock private DeviceContextService deviceContextService;
    private User testUser;

    /** registerDevice 기본 반환 — 새 기기, 추방 없음, 현재 1대, 정상 */
    private DeviceLimitService.DeviceRegistrationResult normalRegistration() {
        return new DeviceLimitService.DeviceRegistrationResult(true, null, 1, false);
    }

    @BeforeEach
    void setUp() {
        // init() 메서드가 dummyHash를 미리 생성하므로 같이 모킹
        given(passwordEncoder.encode(anyString())).willReturn(DUMMY_HASH);
        authService.init();

        testUser = User.builder()
            .email(EMAIL)
            .password(DUMMY_HASH)
            .name("테스트")
            .phone("010-1234-5678")
            .role(UserRole.BUYER)
            .build();
        ReflectionTestUtils.setField(testUser, "id", USER_ID);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  회원가입
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Nested
    @DisplayName("회원가입")
    class Signup {

        @Test
        @DisplayName("성공 — Rate Limit 통과 후 Command 서비스에 위임한다")
        void signup_success() {
            // given
            SignUpRequest request = new SignUpRequest(
                EMAIL, PASSWORD, "테스트", "010-1234-5678", "서울시",
                UserRole.BUYER,
                null, null, null  // SELLER 필드 (BUYER엔 불필요)
            );
            given(authCommandService.signup(request)).willReturn(testUser);

            // when
            var response = authService.signup(request, CLIENT_IP);

            // then
            assertThat(response.email()).isEqualTo(EMAIL);
            verify(rateLimitService).tryConsume(RateLimitPolicy.SIGNUP_PER_IP, CLIENT_IP);
            verify(authCommandService).signup(request);
        }

        @Test
        @DisplayName("실패 — Rate Limit 초과 시 Command 서비스 호출 전에 차단된다")
        void signup_blocked_by_rate_limit() {
            // given
            SignUpRequest request = new SignUpRequest(
                EMAIL, PASSWORD, "테스트", "010-1234-5678", "서울시",
                UserRole.BUYER,
                null, null, null
            );
            doThrow(new CustomException(ErrorCode.AUTH_013))
                .when(rateLimitService).tryConsume(RateLimitPolicy.SIGNUP_PER_IP, CLIENT_IP);

            // when & then
            assertThatThrownBy(() -> authService.signup(request, CLIENT_IP))
                .isInstanceOf(CustomException.class);

            // Rate Limit에서 막혔으니 가입 로직은 호출되지 않아야 함
            verify(authCommandService, never()).signup(any());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  로그인
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("성공 — Rate Limit 3계층을 모두 통과한 뒤 토큰 발급 및 기기 등록까지 수행한다")
        void login_success_full_flow() {
            // given
            LoginRequest request = new LoginRequest(EMAIL, PASSWORD);
            given(authQueryService.authenticate(request, DUMMY_HASH)).willReturn(testUser);
            given(jwtTokenProvider.createAccessToken(USER_ID, EMAIL, UserRole.BUYER)).willReturn(ACCESS_TOKEN);
            given(jwtTokenProvider.createRefreshToken(USER_ID, DEVICE_ID)).willReturn(REFRESH_TOKEN);
            given(deviceLimitService.registerDevice(USER_ID, DEVICE_ID)).willReturn(normalRegistration());
            given(jwtTokenProvider.getRefreshTokenExpirySeconds()).willReturn(604_800L); // 7d
            given(jwtTokenProvider.getAccessTokenExpirySeconds()).willReturn(900L); // 15m

            // when
            LoginResult result = authService.login(request, DEVICE_ID, USER_AGENT, CLIENT_IP);

            // then — Rate Limit 3계층이 모두 호출되었는지
            verify(rateLimitService).tryConsume(RateLimitPolicy.LOGIN_PER_GLOBAL, "all");
            verify(rateLimitService).tryConsume(RateLimitPolicy.LOGIN_PER_IP, CLIENT_IP);
            verify(rateLimitService).tryConsume(RateLimitPolicy.LOGIN_PER_ACCOUNT, EMAIL);

            // 토큰 발급 + 저장
            verify(jwtTokenProvider).createAccessToken(USER_ID, EMAIL, UserRole.BUYER);
            verify(jwtTokenProvider).createRefreshToken(USER_ID, DEVICE_ID);
            verify(deviceLimitService).registerDevice(USER_ID, DEVICE_ID);
            verify(redisTokenService).saveRefreshToken(USER_ID, DEVICE_ID, REFRESH_TOKEN, 604_800L);
            verify(authQueryService).recordLogin(USER_ID);

            // 컨텍스트 각인 (Level 2 보안)
            verify(deviceContextService).bindContext(USER_ID, DEVICE_ID, USER_AGENT, CLIENT_IP);

            // 응답에 RT가 담겨 컨트롤러로 전달되는지
            assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
            assertThat(result.response().accessToken()).isEqualTo(ACCESS_TOKEN);
        }

        @Test
        @DisplayName("실패 — Rate Limit 초과 시 BCrypt(인증) 단계에 절대 도달하지 않는다")
        void login_blocked_before_bcrypt() {
            // given
            LoginRequest request = new LoginRequest(EMAIL, PASSWORD);
            doThrow(new CustomException(ErrorCode.AUTH_013))
                .when(rateLimitService).tryConsume(RateLimitPolicy.LOGIN_PER_GLOBAL, "all");

            // when & then
            assertThatThrownBy(() -> authService.login(request, DEVICE_ID, USER_AGENT, CLIENT_IP))
                .isInstanceOf(CustomException.class);

            // 핵심: BCrypt 검증이나 토큰 발급이 호출되지 않아야 함
            verify(authQueryService, never()).authenticate(any(), anyString());
            verify(jwtTokenProvider, never()).createAccessToken(anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("LRU 추방 발생 — registerDevice가 추방 기기를 반환하면 흐름은 계속 진행된다")
        void login_with_lru_eviction() {
            // given
            LoginRequest request = new LoginRequest(EMAIL, PASSWORD);
            given(authQueryService.authenticate(request, DUMMY_HASH)).willReturn(testUser);
            given(jwtTokenProvider.createAccessToken(USER_ID, EMAIL, UserRole.BUYER)).willReturn(ACCESS_TOKEN);
            given(jwtTokenProvider.createRefreshToken(USER_ID, DEVICE_ID)).willReturn(REFRESH_TOKEN);
            // 5대 한도 초과 → 가장 오래된 기기 "old-device" 추방
            given(deviceLimitService.registerDevice(USER_ID, DEVICE_ID))
                .willReturn(new DeviceLimitService.DeviceRegistrationResult(true, "old-device", 5, false));
            given(jwtTokenProvider.getRefreshTokenExpirySeconds()).willReturn(604_800L);
            given(jwtTokenProvider.getAccessTokenExpirySeconds()).willReturn(900L);

            // when
            LoginResult result = authService.login(request, DEVICE_ID, USER_AGENT, CLIENT_IP);

            // then — 추방이 일어나도 RT 저장은 정상적으로 진행되어야 함
            assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
            verify(redisTokenService).saveRefreshToken(USER_ID, DEVICE_ID, REFRESH_TOKEN, 604_800L);
            verify(redisTokenService).deleteRefreshToken(USER_ID, "old-device");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  토큰 재발급
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Nested
    @DisplayName("토큰 재발급 (RTR + CAS)")
    class Refresh {

        @Test
        @DisplayName("성공 — CAS로 RT를 원자적으로 회전하고 새 AT/RT를 발급한다")
        void refresh_success() {
            // given
            String oldRt = "old-rt";
            TokenRefreshRequest request = new TokenRefreshRequest(oldRt);

            Claims claims = mock(Claims.class);
            given(claims.get("deviceId", String.class)).willReturn(DEVICE_ID);

            given(jwtTokenProvider.parseClaims(oldRt)).willReturn(claims);
            given(jwtTokenProvider.getUserId(claims)).willReturn(USER_ID);
            given(deviceLimitService.isNewDevice(USER_ID, DEVICE_ID)).willReturn(false);
            given(authQueryService.findActiveUser(USER_ID)).willReturn(testUser);
            given(jwtTokenProvider.createAccessToken(USER_ID, EMAIL, UserRole.BUYER)).willReturn(ACCESS_TOKEN);
            given(jwtTokenProvider.createRefreshToken(USER_ID, DEVICE_ID)).willReturn(NEW_REFRESH_TOKEN);
            given(jwtTokenProvider.getRefreshTokenExpirySeconds()).willReturn(604_800L);
            given(jwtTokenProvider.getAccessTokenExpirySeconds()).willReturn(900L);
            given(deviceContextService.verifyContext(USER_ID, DEVICE_ID, USER_AGENT, CLIENT_IP))
                .willReturn(DeviceContextService.ContextVerifyResult.MATCH);
            // CAS 성공
            given(redisTokenService.rotateRefreshTokenCAS(
                USER_ID, DEVICE_ID, oldRt, NEW_REFRESH_TOKEN, 604_800L
            )).willReturn(true);

            // when
            RefreshResult result = authService.refresh(request, DEVICE_ID, USER_AGENT, CLIENT_IP);

            // then
            assertThat(result.newRefreshToken()).isEqualTo(NEW_REFRESH_TOKEN);
            verify(redisTokenService).rotateRefreshTokenCAS(
                USER_ID, DEVICE_ID, oldRt, NEW_REFRESH_TOKEN, 604_800L);
            verify(deviceLimitService).touchDevice(USER_ID, DEVICE_ID);
            verify(deviceContextService).bindContext(USER_ID, DEVICE_ID, USER_AGENT, CLIENT_IP);
        }

        @Test
        @DisplayName("실패 — RT 페이로드 deviceId와 쿠키 deviceId가 다르면 거부 (탈취 의심)")
        void refresh_rejects_when_device_id_mismatched() {
            // given
            String oldRt = "old-rt";
            TokenRefreshRequest request = new TokenRefreshRequest(oldRt);

            Claims claims = mock(Claims.class);
            given(claims.get("deviceId", String.class)).willReturn("other-device-zzz"); // 불일치

            given(jwtTokenProvider.parseClaims(oldRt)).willReturn(claims);

            // when & then
            assertThatThrownBy(() -> authService.refresh(request, DEVICE_ID, USER_AGENT, CLIENT_IP))
                .isInstanceOf(CustomException.class);

            // CAS 호출 자체가 일어나지 않아야 함
            verify(redisTokenService, never()).rotateRefreshTokenCAS(
                anyLong(), anyString(), anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("실패 — ZSET 미등록 기기(isNewDevice=true)면 거부한다 (추방/탈취 의심)")
        void refresh_rejects_when_device_not_registered() {
            // given
            String oldRt = "old-rt";
            TokenRefreshRequest request = new TokenRefreshRequest(oldRt);

            Claims claims = mock(Claims.class);
            given(claims.get("deviceId", String.class)).willReturn(DEVICE_ID);
            given(jwtTokenProvider.parseClaims(oldRt)).willReturn(claims);
            given(jwtTokenProvider.getUserId(claims)).willReturn(USER_ID);
            // 미등록 기기 → 거부
            given(deviceLimitService.isNewDevice(USER_ID, DEVICE_ID)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.refresh(request, DEVICE_ID, USER_AGENT, CLIENT_IP))
                .isInstanceOf(CustomException.class);

            verify(redisTokenService, never()).rotateRefreshTokenCAS(
                anyLong(), anyString(), anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("실패 — CAS 회전 실패 시 예외를 던지고 새 RT는 저장되지 않는다")
        void refresh_fails_when_cas_collides() {
            // given
            String oldRt = "old-rt";
            TokenRefreshRequest request = new TokenRefreshRequest(oldRt);

            Claims claims = mock(Claims.class);
            given(claims.get("deviceId", String.class)).willReturn(DEVICE_ID);

            given(jwtTokenProvider.parseClaims(oldRt)).willReturn(claims);
            given(jwtTokenProvider.getUserId(claims)).willReturn(USER_ID);
            given(deviceLimitService.isNewDevice(USER_ID, DEVICE_ID)).willReturn(false);
            given(authQueryService.findActiveUser(USER_ID)).willReturn(testUser);
            given(jwtTokenProvider.createAccessToken(USER_ID, EMAIL, UserRole.BUYER)).willReturn(ACCESS_TOKEN);
            given(jwtTokenProvider.createRefreshToken(USER_ID, DEVICE_ID)).willReturn(NEW_REFRESH_TOKEN);
            given(jwtTokenProvider.getRefreshTokenExpirySeconds()).willReturn(604_800L);
            // CAS 실패 (저장된 RT와 불일치 — 동시성 충돌 or 탈취)
            given(redisTokenService.rotateRefreshTokenCAS(
                anyLong(), anyString(), anyString(), anyString(), anyLong()
            )).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.refresh(request, DEVICE_ID, USER_AGENT, CLIENT_IP))
                .isInstanceOf(CustomException.class);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  로그아웃 (Best-effort 정책)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("성공 — RT 삭제, 기기 제거, AT 블랙리스트 등록 모두 수행")
        void logout_success() {
            // given
            given(jwtTokenProvider.getJti(ACCESS_TOKEN)).willReturn("jti-123");
            given(jwtTokenProvider.getExpirationSeconds(ACCESS_TOKEN)).willReturn(900L);

            // when
            authService.logout(USER_ID, DEVICE_ID, ACCESS_TOKEN);

            // then
            verify(redisTokenService).deleteRefreshToken(USER_ID, DEVICE_ID);
            verify(deviceLimitService).removeDevice(USER_ID, DEVICE_ID);
            verify(deviceContextService).removeContext(USER_ID, DEVICE_ID);
            verify(redisTokenService).addToBlacklist("jti-123", 900L);
        }

        @Test
        @DisplayName("Best-effort — RT 삭제 실패해도 기기 제거와 블랙리스트는 진행한다")
        void logout_continues_when_rt_deletion_fails() {
            // given
            doThrow(new RuntimeException("Redis down"))
                .when(redisTokenService).deleteRefreshToken(USER_ID, DEVICE_ID);
            given(jwtTokenProvider.getJti(ACCESS_TOKEN)).willReturn("jti-123");
            given(jwtTokenProvider.getExpirationSeconds(ACCESS_TOKEN)).willReturn(900L);

            // when
            authService.logout(USER_ID, DEVICE_ID, ACCESS_TOKEN);

            // then — RT 삭제 실패해도 나머지 두 단계는 실행되어야 함 (best-effort)
            verify(deviceLimitService).removeDevice(USER_ID, DEVICE_ID);
            verify(redisTokenService).addToBlacklist("jti-123", 900L);
        }

        @Test
        @DisplayName("이미 만료된 AT는 블랙리스트에 등록하지 않는다 (메모리 낭비 방지)")
        void logout_skips_blacklist_for_expired_token() {
            // given
            given(jwtTokenProvider.getJti(ACCESS_TOKEN)).willReturn("jti-123");
            given(jwtTokenProvider.getExpirationSeconds(ACCESS_TOKEN)).willReturn(0L); // 만료됨

            // when
            authService.logout(USER_ID, DEVICE_ID, ACCESS_TOKEN);

            // then
            verify(redisTokenService).deleteRefreshToken(USER_ID, DEVICE_ID);
            verify(deviceLimitService).removeDevice(USER_ID, DEVICE_ID);
            verify(redisTokenService, never()).addToBlacklist(anyString(), anyLong());
        }

        @Test
        @DisplayName("AT가 null이어도 RT 삭제와 기기 제거는 정상 수행된다")
        void logout_works_without_access_token() {
            // when
            authService.logout(USER_ID, DEVICE_ID, null);

            // then
            verify(redisTokenService).deleteRefreshToken(USER_ID, DEVICE_ID);
            verify(deviceLimitService).removeDevice(USER_ID, DEVICE_ID);
            verify(redisTokenService, never()).addToBlacklist(anyString(), anyLong());
        }
    }
}
