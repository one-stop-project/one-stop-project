package com.sparta.one_stop.domain.auth.service;

import com.sparta.one_stop.domain.auth.dto.request.LoginRequest;
import com.sparta.one_stop.domain.auth.dto.request.SignUpRequest;
import com.sparta.one_stop.domain.auth.dto.request.TokenRefreshRequest;
import com.sparta.one_stop.domain.auth.dto.result.LoginResult;
import com.sparta.one_stop.domain.auth.dto.result.RefreshResult;
import com.sparta.one_stop.domain.auth.dto.response.SignUpResponse;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import io.jsonwebtoken.Claims; // 인터페이스만 임포트
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

// ★ Assertions 정적 임포트 추가
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

    @Mock
    private UserRepository userRepository;
    @Mock
    private SellerRepository sellerRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RedisTokenService redisTokenService;

    private User testUser;
    private final String DEVICE_ID = "device-123";

    @BeforeEach
    void setUp() {
        given(passwordEncoder.encode(anyString())).willReturn("dummy-hash");
        authService.init();

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
        given(request.password()).willReturn("password123");
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encoded");

        SignUpResponse response = authService.signup(request);

        verify(userRepository, times(1)).save(any(User.class));
        verify(sellerRepository, never()).save(any());
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("회원가입 성공 - 판매자")
    void signup_Success_Seller() {
        SignUpRequest request = mock(SignUpRequest.class);
        given(request.role()).willReturn(UserRole.SELLER);
        given(request.email()).willReturn("seller@test.com");
        given(request.password()).willReturn("password123");
        given(request.shopName()).willReturn("상점명");
        given(request.businessNumber()).willReturn("123-45-67890");

        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encoded");

        SignUpResponse response = authService.signup(request);

        verify(userRepository, times(1)).save(any(User.class));
        verify(sellerRepository, times(1)).save(any());
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("회원가입 실패 - 허용되지 않은 권한 (ADMIN)")
    void signup_Fail_InvalidRole() {
        SignUpRequest request = mock(SignUpRequest.class);
        given(request.role()).willReturn(UserRole.ADMIN);

        CustomException exception = assertThrows(CustomException.class, () -> authService.signup(request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_011);
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void signup_Fail_DuplicateEmail() {
        SignUpRequest request = mock(SignUpRequest.class);
        given(request.role()).willReturn(UserRole.BUYER);
        given(request.email()).willReturn("dup@test.com");
        given(userRepository.existsByEmail("dup@test.com")).willReturn(true);

        CustomException exception = assertThrows(CustomException.class, () -> authService.signup(request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_002);
    }

    @Test
    @DisplayName("회원가입 실패 - 판매자 필수값(상호명) 누락")
    void signup_Fail_SellerMissingShopName() {
        SignUpRequest request = mock(SignUpRequest.class);
        given(request.role()).willReturn(UserRole.SELLER);
        given(request.email()).willReturn("seller@test.com");
        given(request.shopName()).willReturn(""); // 빈 상호명

        CustomException exception = assertThrows(CustomException.class, () -> authService.signup(request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_001);
    }

    @Test
    @DisplayName("회원가입 실패 - 동시성 문제로 인한 DB 제약조건 위반")
    void signup_Fail_DataIntegrityViolation() {
        SignUpRequest request = mock(SignUpRequest.class);
        given(request.role()).willReturn(UserRole.BUYER);

        // 💡 [수정] request의 필수값 모킹 추가
        given(request.email()).willReturn("test@test.com");
        given(request.password()).willReturn("password123");

        given(userRepository.existsByEmail(anyString())).willReturn(false);
        // DB 저장 시 User 객체가 만들어지면서 인코딩이 불리므로 추가
        given(passwordEncoder.encode(anyString())).willReturn("encoded");

        when(userRepository.save(any(User.class))).thenThrow(DataIntegrityViolationException.class);

        CustomException exception = assertThrows(CustomException.class, () -> authService.signup(request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_002);
    }

    // =========================================================================
    // 로그인 (Login) 테스트
    // =========================================================================
    @Test
    @DisplayName("로그인 성공")
    void login_Success() {
        LoginRequest request = mock(LoginRequest.class);
        given(request.email()).willReturn("test@test.com");
        given(request.password()).willReturn("password123");

        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(testUser));
        given(passwordEncoder.matches("password123", testUser.getPassword())).willReturn(true);
        given(jwtTokenProvider.createAccessToken(any(), any())).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(any(), any())).willReturn("refresh-token");

        LoginResult result = authService.login(request, DEVICE_ID);

        verify(redisTokenService, times(1)).saveRefreshToken(any(), eq(DEVICE_ID), anyString(), anyLong());
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.response().accessToken()).isEqualTo("access-token");
    }

    @Test
    @DisplayName("로그인 실패 - 존재하지 않는 계정")
    void login_Fail_UserNotFound() {
        LoginRequest request = mock(LoginRequest.class);
        given(request.email()).willReturn("none@test.com");
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class, () -> authService.login(request, DEVICE_ID));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_004);
        verify(passwordEncoder, times(1)).matches(any(), anyString()); // 타이밍 방어 로직
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치")
    void login_Fail_PasswordMismatch() {
        LoginRequest request = mock(LoginRequest.class);
        given(request.email()).willReturn("test@test.com");
        given(request.password()).willReturn("wrong-password");

        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(testUser));
        given(passwordEncoder.matches("wrong-password", testUser.getPassword())).willReturn(false);

        CustomException exception = assertThrows(CustomException.class, () -> authService.login(request, DEVICE_ID));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_004);
    }

    @Test
    @DisplayName("로그인 실패 - 정지된 계정")
    void login_Fail_SuspendedUser() {
        testUser.suspend();
        LoginRequest request = mock(LoginRequest.class);
        given(request.email()).willReturn("test@test.com");
        given(request.password()).willReturn("password123");

        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(testUser));
        given(passwordEncoder.matches("password123", testUser.getPassword())).willReturn(true);

        CustomException exception = assertThrows(CustomException.class, () -> authService.login(request, DEVICE_ID));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_005);
    }

    // =========================================================================
    // 재발급 (Refresh) 테스트 - ★ 수정된 부분 (Claims 모킹)
    // =========================================================================
    @Test
    @DisplayName("토큰 재발급 성공")
    void refresh_Success() {
        TokenRefreshRequest request = mock(TokenRefreshRequest.class);
        given(request.refreshToken()).willReturn("old-refresh-token");

        // 💡 DefaultClaims 대신 모킹 사용
        Claims claims = mock(Claims.class);
        given(claims.get("deviceId", String.class)).willReturn(DEVICE_ID);

        given(jwtTokenProvider.parseClaims("old-refresh-token")).willReturn(claims);
        given(jwtTokenProvider.getUserId(claims)).willReturn(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));

        given(jwtTokenProvider.createAccessToken(any(), any())).willReturn("new-access-token");
        given(jwtTokenProvider.createRefreshToken(any(), any())).willReturn("new-refresh-token");
        given(redisTokenService.rotateRefreshTokenCAS(any(), anyString(), anyString(), anyString(), anyLong())).willReturn(true);

        RefreshResult result = authService.refresh(request, DEVICE_ID);

        assertThat(result.newRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(result.response().accessToken()).isEqualTo("new-access-token");}

    @Test
    @DisplayName("토큰 재발급 실패 - Device ID 불일치 (탈취 의심)")
    void refresh_Fail_DeviceIdMismatch() {
        TokenRefreshRequest request = mock(TokenRefreshRequest.class);
        given(request.refreshToken()).willReturn("old-refresh-token");

        // 💡 디바이스 아이디 불일치 모킹
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
        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));

        // 💡 [수정] 새 토큰 반환값 모킹 추가 (이 부분이 없어서 null이 들어가 에러가 발생했음)
        given(jwtTokenProvider.createAccessToken(any(), any())).willReturn("new-access-token");
        given(jwtTokenProvider.createRefreshToken(any(), any())).willReturn("new-refresh-token");

        // CAS(원자적 갱신) 실패 상황 모킹
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
        given(jwtTokenProvider.getExpiration(accessToken)).willReturn(3600L);

        authService.logout(1L, DEVICE_ID, accessToken);

        verify(redisTokenService, times(1)).deleteRefreshToken(1L, DEVICE_ID);
        verify(redisTokenService, times(1)).addToBlacklist("jti-123", 3600L);
    }

    @Test
    @DisplayName("로그아웃 성공 - 만료된 토큰은 블랙리스트 추가 안함")
    void logout_Success_ExpiredToken_NoBlacklist() {
        String accessToken = "expired-access-token";
        given(jwtTokenProvider.getJti(accessToken)).willReturn("jti-123");
        given(jwtTokenProvider.getExpiration(accessToken)).willReturn(-10L); // 만료됨

        authService.logout(1L, DEVICE_ID, accessToken);

        verify(redisTokenService, times(1)).deleteRefreshToken(1L, DEVICE_ID);
        verify(redisTokenService, never()).addToBlacklist(anyString(), anyLong());
    }
}
