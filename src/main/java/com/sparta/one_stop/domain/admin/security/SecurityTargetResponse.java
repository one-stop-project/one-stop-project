package com.sparta.one_stop.domain.admin.security;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.enums.user.UserStatus;

public record SecurityTargetResponse(
    Long id,
    String email,
    String name,
    UserRole role,
    UserStatus status,
    boolean sanctionable
) {
    static SecurityTargetResponse from(User user) {
        boolean sanctionableRole = user.getRole() == UserRole.BUYER
            || user.getRole() == UserRole.SELLER;
        boolean sanctionableStatus = user.getStatus() != UserStatus.WITHDRAWN;
        return new SecurityTargetResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getRole(),
            user.getStatus(),
            sanctionableRole && sanctionableStatus
        );
    }
}
