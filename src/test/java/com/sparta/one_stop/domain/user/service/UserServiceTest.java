package com.sparta.one_stop.domain.user.service;

import com.sparta.one_stop.domain.auth.service.RedisTokenService;
import com.sparta.one_stop.domain.user.dto.request.PasswordChangeRequest;
import com.sparta.one_stop.domain.user.dto.request.UserUpdateRequest;
import com.sparta.one_stop.domain.user.dto.request.WithdrawRequest;
import com.sparta.one_stop.domain.user.dto.response.UserMeResponse;
import com.sparta.one_stop.domain.user.dto.response.UserUpdateResponse;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.enums.user.UserStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RedisTokenService redisTokenService;

    private User testUser;
    private final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .email("test@test.com")
            .password("encoded-old-password")
            .name("홍길동")
            .phone("010-1234-5678")
            .address("서울시 강남구")
            .role(UserRole.BUYER)
            .build();
    }

    // =========================================================================
    // 내 정보 조회 (Get My Info)
    // =========================================================================
    @Test
    @DisplayName("내 정보 조회 성공")
    void getMyInfo_Success() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(testUser));

        UserMeResponse response = userService.getMyInfo(USER_ID);

        assertThat(response).isNotNull();
        // UserMeResponse 내부 구조에 맞춰 단언문을 작성하세요.
        // assertThat(response.email()).isEqualTo("test@test.com");
    }

    @Test
    @DisplayName("내 정보 조회 실패 - 사용자 없음")
    void getMyInfo_Fail_UserNotFound() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class, () -> userService.getMyInfo(USER_ID));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_001);
    }

    // =========================================================================
    // 회원 정보 수정 (Update My Info)
    // =========================================================================
    @Test
    @DisplayName("회원 정보 수정 성공")
    void updateMyInfo_Success() {
        UserUpdateRequest request = mock(UserUpdateRequest.class);
        given(request.name()).willReturn("김철수");
        given(request.phone()).willReturn("010-9999-9999");
        given(request.address()).willReturn("부산시");

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(testUser));

        UserUpdateResponse response = userService.updateMyInfo(USER_ID, request);

        assertThat(testUser.getName()).isEqualTo("김철수");
        assertThat(testUser.getPhone()).isEqualTo("010-9999-9999");
        assertThat(testUser.getAddress()).isEqualTo("부산시");
        assertThat(response).isNotNull();
    }

    // =========================================================================
    // 비밀번호 변경 (Change Password)
    // =========================================================================
    @Test
    @DisplayName("비밀번호 변경 성공")
    void changePassword_Success() {
        PasswordChangeRequest request = mock(PasswordChangeRequest.class);
        given(request.currentPassword()).willReturn("old-password");
        given(request.newPassword()).willReturn("new-password");

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(testUser));
        given(passwordEncoder.matches("old-password", testUser.getPassword())).willReturn(true);
        given(passwordEncoder.matches("new-password", testUser.getPassword())).willReturn(false);
        given(passwordEncoder.encode("new-password")).willReturn("encoded-new-password");

        userService.changePassword(USER_ID, request);

        assertThat(testUser.getPassword()).isEqualTo("encoded-new-password");
        // 모든 기기 로그아웃 검증
        verify(redisTokenService, times(1)).deleteAllRefreshTokensByUserId(USER_ID);
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 현재 비밀번호 불일치")
    void changePassword_Fail_WrongCurrentPassword() {
        PasswordChangeRequest request = mock(PasswordChangeRequest.class);
        given(request.currentPassword()).willReturn("wrong-old-password");

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(testUser));
        given(passwordEncoder.matches("wrong-old-password", testUser.getPassword())).willReturn(false);

        CustomException exception = assertThrows(CustomException.class, () -> userService.changePassword(USER_ID, request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_002);
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 기존 비밀번호와 동일한 새 비밀번호")
    void changePassword_Fail_SameAsOldPassword() {
        PasswordChangeRequest request = mock(PasswordChangeRequest.class);
        given(request.currentPassword()).willReturn("old-password");
        given(request.newPassword()).willReturn("old-password");

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(testUser));
        given(passwordEncoder.matches("old-password", testUser.getPassword())).willReturn(true); // 현재 비번 맞음
        given(passwordEncoder.matches("old-password", testUser.getPassword())).willReturn(true); // 새 비번도 기존과 동일함 (true 반환)

        CustomException exception = assertThrows(CustomException.class, () -> userService.changePassword(USER_ID, request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_003);
    }

    // =========================================================================
    // 회원 탈퇴 (Withdraw)
    // =========================================================================
    @Test
    @DisplayName("회원 탈퇴 성공 (Soft Delete)")
    void withdraw_Success() {
        WithdrawRequest request = mock(WithdrawRequest.class);
        given(request.password()).willReturn("password123");

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(testUser));
        given(passwordEncoder.matches("password123", testUser.getPassword())).willReturn(true);

        userService.withdraw(USER_ID, request);

        // Soft Delete 상태 확인
        assertThat(testUser.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        // 탈퇴 후 토큰 전체 삭제 검증
        verify(redisTokenService, times(1)).deleteAllRefreshTokensByUserId(USER_ID);
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 비밀번호 불일치")
    void withdraw_Fail_PasswordMismatch() {
        WithdrawRequest request = mock(WithdrawRequest.class);
        given(request.password()).willReturn("wrong-password");

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(testUser));
        given(passwordEncoder.matches("wrong-password", testUser.getPassword())).willReturn(false);

        CustomException exception = assertThrows(CustomException.class, () -> userService.withdraw(USER_ID, request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_002);

        // 상태가 변경되지 않았는지 확인
        assertThat(testUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
