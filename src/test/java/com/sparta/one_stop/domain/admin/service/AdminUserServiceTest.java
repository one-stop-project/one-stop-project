package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.admin.repository.AdminActionHistoryRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserService 테스트")
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AdminActionHistoryRepository adminActionHistoryRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AdminUserService adminUserService;

    private static final Long ACTOR_ID = 1L;
    private static final Long TARGET_ID = 99L;

    private User buyerUser() {
        User user = User.builder()
            .email("buyer@test.com").password("pass").name("구매자").role(UserRole.BUYER).build();
        ReflectionTestUtils.setField(user, "id", TARGET_ID);
        return user;
    }

    private User sellerUser() {
        User user = User.builder()
            .email("seller@test.com").password("pass").name("판매자").role(UserRole.SELLER).build();
        ReflectionTestUtils.setField(user, "id", TARGET_ID);
        return user;
    }

    private User adminUser() {
        User user = User.builder()
            .email("admin@test.com").password("pass").name("관리자").role(UserRole.BUYER).build();
        ReflectionTestUtils.setField(user, "id", TARGET_ID);
        user.updateRole(UserRole.ADMIN);
        return user;
    }

    private User superAdminUser() {
        User user = User.builder()
            .email("super@test.com").password("pass").name("최고관리자").role(UserRole.BUYER).build();
        ReflectionTestUtils.setField(user, "id", TARGET_ID);
        user.updateRole(UserRole.SUPER_ADMIN);
        return user;
    }

    // ─────────────────────────────────────────────────────────────
    //  관리자 권한 부여
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("grantAdmin()")
    class GrantAdmin {

        @Test
        @DisplayName("BUYER 회원에게 ADMIN 권한 부여 → role이 ADMIN으로 변경되고 이력이 저장된다")
        void grant_admin_to_buyer_success() {
            User target = buyerUser();
            given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));

            adminUserService.grantAdmin(TARGET_ID, ACTOR_ID);

            assertThat(target.getRole()).isEqualTo(UserRole.ADMIN);
            verify(adminActionHistoryRepository).save(any());
        }

        @Test
        @DisplayName("SELLER에게 ADMIN 권한 부여 → ADMIN_011 예외")
        void grant_admin_to_seller_throws() {
            given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(sellerUser()));

            assertThatThrownBy(() -> adminUserService.grantAdmin(TARGET_ID, ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.ADMIN_011));
        }

        @Test
        @DisplayName("이미 ADMIN인 회원에게 권한 부여 → ADMIN_012 예외")
        void grant_admin_to_already_admin_throws() {
            given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(adminUser()));

            assertThatThrownBy(() -> adminUserService.grantAdmin(TARGET_ID, ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.ADMIN_012));
        }

        @Test
        @DisplayName("존재하지 않는 회원에게 권한 부여 → MEMBER_001 예외")
        void grant_admin_not_found_throws() {
            given(userRepository.findById(TARGET_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.grantAdmin(TARGET_ID, ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.MEMBER_001));
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  관리자 권한 회수
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("revokeAdmin()")
    class RevokeAdmin {

        @Test
        @DisplayName("ADMIN 회원 권한 회수 → role이 BUYER로 변경되고 이력이 저장된다")
        void revoke_admin_success() {
            User target = adminUser();
            given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));

            adminUserService.revokeAdmin(TARGET_ID, ACTOR_ID);

            assertThat(target.getRole()).isEqualTo(UserRole.BUYER);
            verify(adminActionHistoryRepository).save(any());
        }

        @Test
        @DisplayName("본인 계정 권한 회수 → ADMIN_014 예외")
        void revoke_own_admin_throws() {
            User target = adminUser();
            given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));

            assertThatThrownBy(() -> adminUserService.revokeAdmin(TARGET_ID, TARGET_ID)) // actorId == targetId
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.ADMIN_014));
        }

        @Test
        @DisplayName("SUPER_ADMIN 권한 회수 → ADMIN_013 예외")
        void revoke_super_admin_throws() {
            given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(superAdminUser()));

            assertThatThrownBy(() -> adminUserService.revokeAdmin(TARGET_ID, ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.ADMIN_013));
        }

        @Test
        @DisplayName("ADMIN이 아닌 회원 권한 회수 → ADMIN_015 예외")
        void revoke_non_admin_throws() {
            given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(buyerUser()));

            assertThatThrownBy(() -> adminUserService.revokeAdmin(TARGET_ID, ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.ADMIN_015));
        }
    }
}
