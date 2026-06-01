package com.sparta.one_stop.domain.user.service;

import com.sparta.one_stop.domain.auth.event.AllDevicesLogoutEvent;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * UserService 테스트
 *
 * <p><b>검증 목적</b>
 * <ul>
 *   <li>조회 / 수정 — 캐시 정합성 (수정 후 evict 호출)</li>
 *   <li>비밀번호 변경 — 현재 비번 검증, 동일 비번 거부, AllDevicesLogoutEvent 발행</li>
 *   <li>탈퇴 — Soft Delete(상태 변경), 캐시 evict, 이벤트 발행</li>
 *   <li>OAuth2 사용자 — 탈퇴 시 비밀번호 검증을 건너뛴다</li>
 * </ul>
 *
 * <p><b>핵심 인사이트</b>:
 * 회원 도메인 변경 시 <b>1) 엔티티 변경 → 2) 캐시 무효화 → 3) 이벤트 발행</b>
 * 순서가 지켜져야 보안 정책(전체 기기 로그아웃)이 작동한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 테스트")
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private UserStatusCacheService userStatusCacheService;

    private User testUser;
    private User oauth2User;

    private static final Long USER_ID = 1L;
    private static final Long OAUTH_USER_ID = 2L;
    private static final String CURRENT_ENCODED = "$2a$10$current.encoded";
    private static final String NEW_ENCODED = "$2a$10$new.encoded";

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .email("buyer@example.com")
            .password(CURRENT_ENCODED)
            .name("구매자")
            .phone("010-1111-2222")
            .role(UserRole.BUYER)
            .build();
        ReflectionTestUtils.setField(testUser, "id", USER_ID);

        // OAuth2 사용자: 빌더로는 provider 못 넣음 → linkOAuth2() 메서드로 연동
        oauth2User = User.builder()
            .email("kakao@example.com")
            .password(null)
            .name("카카오유저")
            .phone(null)
            .role(UserRole.BUYER)
            .build();
        ReflectionTestUtils.setField(oauth2User, "id", OAUTH_USER_ID);
        oauth2User.linkOAuth2("kakao", "kakao-12345");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  내 정보 조회
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Nested
    @DisplayName("내 정보 조회")
    class GetMyInfo {

        @Test
        @DisplayName("성공 — 사용자 정보가 응답 DTO로 매핑된다")
        void getMyInfo_success() {
            // given
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(testUser));

            // when
            UserMeResponse response = userService.getMyInfo(USER_ID);

            // then
            assertThat(response.email()).isEqualTo("buyer@example.com");
            assertThat(response.name()).isEqualTo("구매자");
        }

        @Test
        @DisplayName("실패 — 존재하지 않는 사용자")
        void getMyInfo_user_not_found() {
            // given
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.getMyInfo(USER_ID))
                .isInstanceOf(CustomException.class);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  내 정보 수정
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Nested
    @DisplayName("내 정보 수정")
    class UpdateMyInfo {

        @Test
        @DisplayName("성공 — 이름과 전화번호가 변경된다")
        void updateMyInfo_success() {
            // given
            UserUpdateRequest request = new UserUpdateRequest("변경된이름", "010-9999-8888", "경기도");
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(testUser));

            // when
            UserUpdateResponse response = userService.updateMyInfo(USER_ID, request);

            // then
            assertThat(response.name()).isEqualTo("변경된이름");
            assertThat(testUser.getName()).isEqualTo("변경된이름");
            assertThat(testUser.getPhone()).isEqualTo("010-9999-8888");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  비밀번호 변경 — 가장 보안 중요한 케이스
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePassword {

        @Test
        @DisplayName("성공 — 비밀번호 변경 + 전체 기기 로그아웃 이벤트 발행")
        void changePassword_success_publishes_logout_event() {
            // given
            PasswordChangeRequest request = new PasswordChangeRequest("OldPass1!", "NewPass1!");
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches("OldPass1!", CURRENT_ENCODED)).willReturn(true);
            given(passwordEncoder.encode("NewPass1!")).willReturn(NEW_ENCODED);

            // when
            userService.changePassword(USER_ID, request);

            // then — 비밀번호 변경
            assertThat(testUser.getPassword()).isEqualTo(NEW_ENCODED);

            // ★ 핵심: AllDevicesLogoutEvent가 발행되어야 모든 기기 강제 로그아웃
            ArgumentCaptor<AllDevicesLogoutEvent> captor = ArgumentCaptor.forClass(AllDevicesLogoutEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
            assertThat(captor.getValue().reason()).isEqualTo("PASSWORD_CHANGED");
        }

        @Test
        @DisplayName("실패 — 현재 비밀번호가 틀리면 변경 거부")
        void changePassword_fails_when_current_password_wrong() {
            // given
            PasswordChangeRequest request = new PasswordChangeRequest("WrongPass1!", "NewPass1!");
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches("WrongPass1!", CURRENT_ENCODED)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> userService.changePassword(USER_ID, request))
                .isInstanceOf(CustomException.class);

            // 비밀번호도 안 바뀌고 이벤트도 발행 안 됨
            assertThat(testUser.getPassword()).isEqualTo(CURRENT_ENCODED);
            verify(eventPublisher, never()).publishEvent(any(AllDevicesLogoutEvent.class));
        }

        @Test
        @DisplayName("실패 — 현재 비밀번호와 새 비밀번호가 같으면 거부")
        void changePassword_fails_when_same_as_current() {
            // given
            PasswordChangeRequest request = new PasswordChangeRequest("SamePass1!", "SamePass1!");
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches("SamePass1!", CURRENT_ENCODED)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.changePassword(USER_ID, request))
                .isInstanceOf(CustomException.class);

            verify(passwordEncoder, never()).encode(anyString());
            verify(eventPublisher, never()).publishEvent(any(AllDevicesLogoutEvent.class));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  회원 탈퇴
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Nested
    @DisplayName("회원 탈퇴")
    class Withdraw {

        @Test
        @DisplayName("성공 — Soft Delete + 캐시 evict + 이벤트 발행")
        void withdraw_success() {
            // given
            WithdrawRequest request = new WithdrawRequest("Password1!");
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches("Password1!", CURRENT_ENCODED)).willReturn(true);

            // when
            userService.withdraw(USER_ID, request);

            // then
            // 1) Soft Delete (상태만 변경, 행 삭제 X)
            assertThat(testUser.getStatus()).isEqualTo(UserStatus.WITHDRAWN);

            // 2) 캐시 무효화 (다음 요청 시 캐시가 ACTIVE 상태로 잘못 응답하지 않도록)
            verify(userStatusCacheService).evict(USER_ID);

            // 3) 이벤트 발행 (모든 기기 RT 일괄 삭제용)
            ArgumentCaptor<AllDevicesLogoutEvent> captor = ArgumentCaptor.forClass(AllDevicesLogoutEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().reason()).isEqualTo("WITHDRAWN");
        }

        @Test
        @DisplayName("실패 — 비밀번호 불일치 시 탈퇴 거부 (상태 변경/캐시/이벤트 모두 발생 X)")
        void withdraw_fails_when_password_wrong() {
            // given
            WithdrawRequest request = new WithdrawRequest("WrongPass!");
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches("WrongPass!", CURRENT_ENCODED)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> userService.withdraw(USER_ID, request))
                .isInstanceOf(CustomException.class);

            assertThat(testUser.getStatus()).isNotEqualTo(UserStatus.WITHDRAWN);
            verify(userStatusCacheService, never()).evict(any());
            verify(eventPublisher, never()).publishEvent(any(AllDevicesLogoutEvent.class));
        }

        @Test
        @DisplayName("OAuth2 사용자 — 비밀번호 검증을 건너뛰고 탈퇴 성공")
        void withdraw_oauth2_user_skips_password_check() {
            // given
            WithdrawRequest request = new WithdrawRequest(null);
            given(userRepository.findById(OAUTH_USER_ID)).willReturn(Optional.of(oauth2User));

            // when
            userService.withdraw(OAUTH_USER_ID, request);

            // then
            assertThat(oauth2User.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
            // 핵심: OAuth2 유저는 비밀번호가 없으므로 matches 호출 자체가 일어나면 안 됨
            verify(passwordEncoder, never()).matches(anyString(), anyString());
            verify(userStatusCacheService).evict(OAUTH_USER_ID);
            verify(eventPublisher).publishEvent(any(AllDevicesLogoutEvent.class));
        }
    }
}
