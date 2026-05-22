package com.sparta.one_stop.domain.user.entity;

import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.enums.user.UserStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users",
    indexes = {
        @Index(name = "idx_user_email", columnList = "email", unique = true),
        @Index(name = "idx_user_provider", columnList = "provider, provider_id", unique = true),
        @Index(name = "idx_user_status", columnList = "status")
    }
)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "address")
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    // OAuth2 Provider (kakao/google/naver), 일반 가입은 null
    @Column(name = "provider", length = 20)
    private String provider;

    // OAuth2 Provider 측 사용자 ID
    @Column(name = "provider_id", length = 255)
    private String providerId;

    /** 마지막 로그인 시간 — 운영 가시성 */
    @Column(name = "last_login_at")
    private java.time.LocalDateTime lastLoginAt;

    @Builder
    private User(String email, String password, String name,
                 String phone, String address, UserRole role) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.role = role;
        this.status = UserStatus.ACTIVE;
    }

    // domain별 필요 로직

    public void updateProfile(String name, String phone, String address) {
        if (name != null) this.name = name;
        if (phone != null) this.phone = phone;
        if (address != null) this.address = address;
    }


    // 비밀번호 변경
    public void changePassword(String newEncodedPassword) {
        if (newEncodedPassword == null || newEncodedPassword.isBlank()) {
            throw new CustomException(ErrorCode.MEMBER_002);
        }
        this.password = newEncodedPassword;
    }

    // 비밀번호 재설정
    public void resetPassword(String newEncodedPassword) {
        this.password = newEncodedPassword;
    }


    // 회원 탈퇴(Soft Delete)
    public void withdraw() {
        if (this.status == UserStatus.WITHDRAWN) {
            throw new CustomException(ErrorCode.MEMBER_004);
        }
        this.status = UserStatus.WITHDRAWN;
    }

    // 판매자 강제 비활성화 시 사용
    public void suspend() {
        if (this.status == UserStatus.WITHDRAWN) {
            throw new CustomException(ErrorCode.MEMBER_004);  // 탈퇴자는 정지 못 함
        }
        this.status = UserStatus.SUSPENDED;
    }

    // 정지 해제
    public void reactivate() {
        if (this.status != UserStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.MEMBER_005);
        }
        this.status = UserStatus.ACTIVE;
    }

    // OAuth2 계정 연결 — 신규 가입 시 한 번만 호출
    public void linkOAuth2(String provider, String providerId) {
        if (this.provider != null) {
            throw new CustomException(ErrorCode.AUTH_016, "이미 OAuth2 계정이 연결되어 있습니다");
        }
        this.provider = provider;
        this.providerId = providerId;
    }

    // 로그인 시간 갱신 / 호출 시점: AuthService.login 성공 직후
    public void recordLogin() {
        this.lastLoginAt = java.time.LocalDateTime.now();
    }

    // 본인검증 / → AuthUser.verifyOwnership(targetUserId)로 호출 권장
    public void verifyOwnership(Long requestUserId) {
        if (!this.id.equals(requestUserId)) {
            throw new CustomException(ErrorCode.AUTH_011);
        }
    }

    // 활성 상태 검증 -> 로그인/API 호출 시
    public void verifyActive() {
        if (this.status == UserStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.AUTH_005);
        }
        if (this.status == UserStatus.WITHDRAWN) {
            throw new CustomException(ErrorCode.AUTH_006);
        }
    }

    public void verifyRole(UserRole requiredRole) {
        if (this.role != requiredRole) {
            throw new CustomException(ErrorCode.AUTH_011);
        }
    }

    public void verifyActiveAndRole(UserRole requiredRole) {
        verifyActive();
        verifyRole(requiredRole);
    }

    // ----- 비즈니스 정책 메서드 -----

    public boolean canPurchase() {
        return this.role == UserRole.BUYER && isActive();
    }

    public boolean canSell() {
        return this.role == UserRole.SELLER && isActive();
    }

    public boolean isAdmin() {
        return this.role == UserRole.ADMIN || this.role == UserRole.SUPER_ADMIN;
    }

    public boolean isOAuth2User() {
        return this.provider != null;
    }



    // ----- 상태 조회 -----

    public boolean isActive() {
        return this.status.isActive();
    }

    public boolean isSuspended() {
        return this.status.isSuspended();
    }

    public boolean isWithdrawn() {
        return this.status == UserStatus.WITHDRAWN;
    }
}
