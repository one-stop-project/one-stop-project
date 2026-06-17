package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.admin.dto.AdminUserResponse;
import com.sparta.one_stop.domain.admin.entity.AdminActionHistory;
import com.sparta.one_stop.domain.admin.repository.AdminActionHistoryRepository;
import com.sparta.one_stop.domain.auth.event.AllDevicesLogoutEvent;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.admin.AdminActionTarget;
import com.sparta.one_stop.global.enums.admin.AdminActionType;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final AdminActionHistoryRepository adminActionHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Page<AdminUserResponse> getAdminUsers(Pageable pageable) {
        return userRepository.findAllByRoleIn(List.of(UserRole.ADMIN, UserRole.SUPER_ADMIN), pageable)
            .map(AdminUserResponse::from);
    }

    @Transactional
    public void grantAdmin(Long targetUserId, Long actorId) {
        User target = userRepository.findById(targetUserId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));

        if (target.getRole() == UserRole.SELLER) {
            throw new CustomException(ErrorCode.ADMIN_011);
        }
        if (target.getRole() == UserRole.ADMIN || target.getRole() == UserRole.SUPER_ADMIN) {
            throw new CustomException(ErrorCode.ADMIN_012);
        }
        // 활성 회원만 승격 가능 (정지/탈퇴 회원 승격 차단)
        if (target.getStatus() != com.sparta.one_stop.global.enums.user.UserStatus.ACTIVE) {
            throw new CustomException(ErrorCode.ADMIN_014);
        }

        target.updateRole(UserRole.ADMIN);

        // role 변경은 기존 AT의 role 클레임을 즉시 무효화해야 함 (필터는 클레임 role을 신뢰)
        //   - increaseTokenVersion: DB 영속 무효화 (Redis 장애에도 유지)
        //   - AllDevicesLogoutEvent: 커밋 후 Listener가 iat-cutoff 등록 + tokenVersion 캐시 evict + RT 정리
        // 효과: 대상자의 구 AT 거부 → 재로그인 시 새 role(ADMIN) 반영
        eventPublisher.publishEvent(new AllDevicesLogoutEvent(targetUserId, "ADMIN_GRANTED"));

        adminActionHistoryRepository.save(AdminActionHistory.builder()
            .actorId(actorId)
            .targetType(AdminActionTarget.ADMIN_USER)
            .targetId(targetUserId)
            .action(AdminActionType.GRANT_ADMIN)
            .build());
    }

    @Transactional
    public void revokeAdmin(Long targetUserId, Long actorId) {
        User target = userRepository.findById(targetUserId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));

        if (targetUserId.equals(actorId)) {
            throw new CustomException(ErrorCode.ADMIN_014);
        }
        if (target.getRole() == UserRole.SUPER_ADMIN) {
            throw new CustomException(ErrorCode.ADMIN_013);
        }
        if (target.getRole() != UserRole.ADMIN) {
            throw new CustomException(ErrorCode.ADMIN_015);
        }

        target.updateRole(UserRole.BUYER);

        // 권한 회수의 핵심 — 구 AT는 여전히 role=ADMIN이므로 즉시 무효화 필수
        //   (이 케이스는 status 변경이 없어 verifyActiveByCache로는 안 잡힘 → 토큰 무효화에 전적으로 의존)
        eventPublisher.publishEvent(new AllDevicesLogoutEvent(targetUserId, "ADMIN_REVOKED"));

        adminActionHistoryRepository.save(AdminActionHistory.builder()
            .actorId(actorId)
            .targetType(AdminActionTarget.ADMIN_USER)
            .targetId(targetUserId)
            .action(AdminActionType.REVOKE_ADMIN)
            .build());
    }
}
