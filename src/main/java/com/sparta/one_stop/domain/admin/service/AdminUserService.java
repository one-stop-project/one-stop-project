package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.admin.dto.AdminUserResponse;
import com.sparta.one_stop.domain.admin.entity.AdminActionHistory;
import com.sparta.one_stop.domain.admin.repository.AdminActionHistoryRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.admin.AdminActionTarget;
import com.sparta.one_stop.global.enums.admin.AdminActionType;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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

        target.updateRole(UserRole.ADMIN);

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

        if (target.getRole() == UserRole.SUPER_ADMIN) {
            throw new CustomException(ErrorCode.ADMIN_013);
        }
        if (targetUserId.equals(actorId)) {
            throw new CustomException(ErrorCode.ADMIN_014);
        }
        if (target.getRole() != UserRole.ADMIN) {
            throw new CustomException(ErrorCode.ADMIN_015);
        }

        target.updateRole(UserRole.BUYER);

        adminActionHistoryRepository.save(AdminActionHistory.builder()
            .actorId(actorId)
            .targetType(AdminActionTarget.ADMIN_USER)
            .targetId(targetUserId)
            .action(AdminActionType.REVOKE_ADMIN)
            .build());
    }
}
