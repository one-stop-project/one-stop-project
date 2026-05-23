package com.sparta.one_stop.domain.auth.service;

import com.sparta.one_stop.domain.auth.dto.request.LoginRequest;
import com.sparta.one_stop.domain.auth.dto.request.SignUpRequest;
import com.sparta.one_stop.domain.auth.dto.request.TokenRefreshRequest;
import com.sparta.one_stop.domain.auth.dto.result.LoginResult;
import com.sparta.one_stop.domain.auth.dto.result.RefreshResult;
import com.sparta.one_stop.domain.auth.dto.response.SignUpResponse;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.ratelimit.RateLimitPolicy;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.ratelimit.RateLimitService;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    // 현재 AuthService가 의존하는 실제 의존성들
    @Mock private AuthQueryService authQueryService;
    @Mock private AuthCommandService authCommandService;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RedisTokenService redisTokenService;
    @Mock private DeviceLimitService deviceLimitService;
    @Mock private RateLimitService rateLimitService;

    private User testUser;
    private final String DEVICE_ID = "device-123";
    private final String CLIENT_IP = "127.0.0.1";
    private final String DUMMY_HASH = "dummy-hash";

    @BeforeEach
    void setUp() {
        // init() 메서드에서 사용되는 PasswordEncoder 모킹
        given(passwordEncoder.encode(anyString())).willReturn(DUMMY_HASH);
        authService.init(); // 더미 해시 생성

        testUser = User.builder()
            .email("test@test.com")
            .password("encoded-password")
            .name("홍길동")
            .phone("010-1234-5678")
            .address("서울시")
            .role(UserRole.BUYER)
            .build();
    }

    // =========================================================================
    // 회원가입 (Signup) 테스트
    // =========================================================================
    @Test
    @DisplayName("회원가입 성공 - 구매자")
    void signup_Success_Buyer() {
        SignUpRequest request = mock(SignUpRequest.class);
        given(request.role()).willReturn(UserRole.BUYER);
        given(request.email()).willReturn("test@test.com");

        given(userRepository.existsByEmail("test@test.com")).willReturn(false);
        // DB 저장은 AuthCommandService로 위임됨
        given(authCommandService.signup(request)).willReturn(testUser);

        SignUpResponse response = authService.signup(request, CLIENT_IP);

        verify(rateLimitService, times(1)).tryConsume(RateLimitPolicy.SIGNUP_PER_IP, CLIENT_IP);
        verify(authCommandService, times(1)).signup(request);
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("회원가입 성공 - 판매자")
    void signup_Success_Seller() {
        SignUpRequest request = mock(SignUpRequest.class);
        given(request.role()).willReturn(UserRole.SELLER);
        given(request.email()).willReturn("seller@test.com");
        given(request.shopName()).willReturn("상점명");
        given(request.businessNumber()).willReturn("123-45-67890");

        given(userRepository.existsByEmail("seller@test.com")).willReturn(false);
        given(authCommandService.signup(request)).willReturn(testUser);

        SignUpResponse response = authService.signup(request, CLIENT_IP);

        verify(authCommandService, times(1)).signup(request);
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("회원가입 실패 - 허용되지 않은 권한 (ADMIN)")
    void signup_Fail_InvalidRole() {
        SignUpRequest request = mock(SignUpRequest.class);
        given(request.role()).willReturn(UserRole.ADMIN);

        CustomException exception = assertThrows(CustomException.class, () -> authService.signup(request, CLIENT_IP));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_011);
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void signup_Fail_DuplicateEmail() {
        SignUpRequest request = mock(SignUpRequest.class);
        given(request.role()).willReturn(UserRole.BUYER);
        given(request.email()).willReturn("dup@test.com");

        given(userRepository.existsByEmail("dup@test.com")).willReturn(true);

        CustomException exception = assertThrows(CustomException.class, () -> authService.signup(request, CLIENT_IP));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_002);
    }

    @Test
    @DisplayName("회원가입 실패 - 판매자 필수값(상호명) 누락")
    void signup_Fail_SellerMissingShopName() {
        SignUpRequest request = mock(SignUpRequest.class);
        given(request.role()).willReturn(UserRole.SELLER);
        // shopName이 null 또는 blank인 상황 시뮬레이션
        given(request.shopName()).willReturn("");

        CustomException exception = assertThrows(CustomException.class, () -> authService.signup(request, CLIENT_IP));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SELLER_010); // 코드 기반 수정 (COMMON_001 -> SELLER_010)
    }

    // =========================================================================
    // 로그인 (Login) 테스트
    // =========================================================================
    @Test
    @DisplayName("로그인 성공")
    void login_Success() {
        LoginRequest request = mock(LoginRequest.class);
        given(request.email()).willReturn("test@test.com");

        // 인증 검증은 AuthQueryService로 위임됨
        given(authQueryService.authenticate(request, DUMMY_HASH)).willReturn(testUser);
        given(jwtTokenProvider.createAccessToken(any(), any())).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(any(), any())).willReturn("refresh-token");
        given(deviceLimitService.registerDevice(any(), eq(DEVICE_ID))).willReturn(null);

        LoginResult result = authService.login(request, DEVICE_ID, CLIENT_IP);

        // RateLimit 호출 검증
        verify(rateLimitService, times(1)).tryConsume(RateLimitPolicy.LOGIN_PER_GLOBAL, "all");
        verify(rateLimitService, times(1)).tryConsume(RateLimitPolicy.LOGIN_PER_IP, CLIENT_IP);
        verify(rateLimitService, times(1)).tryConsume(RateLimitPolicy.LOGIN_PER_ACCOUNT, "test@test.com");

        verify(redisTokenService, times(1)).saveRefreshToken(any(), eq(DEVICE_ID), anyString(), anyLong());
        verify(authQueryService, times(1)).recordLogin(any());

        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.response().accessToken()).isEqualTo("access-token");
    }

    @Test
    @DisplayName("로그인 실패 - 존재하지 않는 계정 또는 비밀번호 불일치")
    void login_Fail_InvalidCredentials() {
        LoginRequest request = mock(LoginRequest.class);
        given(request.email()).willReturn("test@test.com");

        // AuthQueryService가 예외를 던진다고 모킹 (비밀번호 검증 등 내부 로직은 여기서 처리됨)
        given(authQueryService.authenticate(request, DUMMY_HASH))
            .willThrow(new CustomException(ErrorCode.AUTH_004));

        CustomException exception = assertThrows(CustomException.class, () -> authService.login(request, DEVICE_ID, CLIENT_IP));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_004);
    }

    // =========================================================================
    // 재발급 (Refresh) 테스트
    // =========================================================================
    @Test
    @DisplayName("토큰 재발급 성공")
    void refresh_Success() {
        TokenRefreshRequest request = mock(TokenRefreshRequest.class);
        given(request.refreshToken()).willReturn("old-refresh-token");

        Claims claims = mock(Claims.class);
        given(claims.get("deviceId", String.class)).willReturn(DEVICE_ID);

        given(jwtTokenProvider.parseClaims("old-refresh-token")).willReturn(claims);
        given(jwtTokenProvider.getUserId(claims)).willReturn(1L);

        // UserRepository.findById 대신 AuthQueryService.findActiveUser 사용
        given(authQueryService.findActiveUser(1L)).willReturn(testUser);

        given(jwtTokenProvider.createAccessToken(any(), any())).willReturn("new-access-token");
        given(jwtTokenProvider.createRefreshToken(any(), any())).willReturn("new-refresh-token");
        given(redisTokenService.rotateRefreshTokenCAS(any(), anyString(), anyString(), anyString(), anyLong())).willReturn(true);

        RefreshResult result = authService.refresh(request, DEVICE_ID);

        verify(deviceLimitService, times(1)).touchDevice(any(), eq(DEVICE_ID));
        assertThat(result.newRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(result.response().accessToken()).isEqualTo("new-access-token");
    }

    @Test
    @DisplayName("토큰 재발급 실패 - Device ID 불일치 (탈취 의심)")
    void refresh_Fail_DeviceIdMismatch() {
        TokenRefreshRequest request = mock(TokenRefreshRequest.class);
        given(request.refreshToken()).willReturn("old-refresh-token");

        Claims claims = mock(Claims.class);
        given(claims.get("deviceId", String.class)).willReturn("hacked-device-id");

        given(jwtTokenProvider.parseClaims(anyString())).willReturn(claims);

        CustomException exception = assertThrows(CustomException.class, () -> authService.refresh(request, DEVICE_ID));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_010);
    }

    @Test
    @DisplayName("토큰 재발급 실패 - Redis 원자적 갱신 실패")
    void refresh_Fail_RedisRotationFail() {
        TokenRefreshRequest request = mock(TokenRefreshRequest.class);
        given(request.refreshToken()).willReturn("old-refresh-token");

        Claims claims = mock(Claims.class);
        given(claims.get("deviceId", String.class)).willReturn(DEVICE_ID);

        given(jwtTokenProvider.parseClaims(anyString())).willReturn(claims);
        given(jwtTokenProvider.getUserId(claims)).willReturn(1L);
        given(authQueryService.findActiveUser(1L)).willReturn(testUser);

        given(jwtTokenProvider.createAccessToken(any(), any())).willReturn("new-access-token");
        given(jwtTokenProvider.createRefreshToken(any(), any())).willReturn("new-refresh-token");

        given(redisTokenService.rotateRefreshTokenCAS(any(), anyString(), anyString(), anyString(), anyLong())).willReturn(false);

        CustomException exception = assertThrows(CustomException.class, () -> authService.refresh(request, DEVICE_ID));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_010);
    }

    // =========================================================================
    // 로그아웃 (Logout) 테스트
    // =========================================================================
    @Test
    @DisplayName("로그아웃 성공")
    void logout_Success() {
        String accessToken = "valid-access-token";
        given(jwtTokenProvider.getJti(accessToken)).willReturn("jti-123");
        given(jwtTokenProvider.getExpirationSeconds(accessToken)).willReturn(3600L);

        authService.logout(1L, DEVICE_ID, accessToken);

        verify(redisTokenService, times(1)).deleteRefreshToken(1L, DEVICE_ID);
        verify(deviceLimitService, times(1)).removeDevice(1L, DEVICE_ID); // 추가된 로직 반영
        verify(redisTokenService, times(1)).addToBlacklist("jti-123", 3600L);
    }

    @Test
    @DisplayName("로그아웃 성공 - 만료된 토큰은 블랙리스트 추가 안함")
    void logout_Success_ExpiredToken_NoBlacklist() {
        String accessToken = "expired-access-token";
        given(jwtTokenProvider.getJti(accessToken)).willReturn("jti-123");
        given(jwtTokenProvider.getExpirationSeconds(accessToken)).willReturn(-10L); // 만료됨

        authService.logout(1L, DEVICE_ID, accessToken);

        verify(redisTokenService, times(1)).deleteRefreshToken(1L, DEVICE_ID);
        verify(deviceLimitService, times(1)).removeDevice(1L, DEVICE_ID);
        verify(redisTokenService, never()).addToBlacklist(anyString(), anyLong());
    }
}
