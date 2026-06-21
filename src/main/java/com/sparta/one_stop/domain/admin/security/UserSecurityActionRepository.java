package com.sparta.one_stop.domain.admin.security;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSecurityActionRepository extends JpaRepository<UserSecurityAction, Long> {
    Optional<UserSecurityAction> findFirstByTargetUserIdAndActionTypeAndActiveTrueOrderByStartedAtDesc(
        Long userId,
        SecurityActionType actionType
    );

    default Optional<UserSecurityAction> findActiveSuspendAction(Long userId) {
        return findFirstByTargetUserIdAndActionTypeAndActiveTrueOrderByStartedAtDesc(
            userId,
            SecurityActionType.SUSPEND
        );
    }
}
