package com.sparta.one_stop.domain.admin.dto;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.enums.user.UserStatus;

import java.time.LocalDateTime;

public record AdminUserResponse(
    Long id,
    String email,
    String name,
    UserRole role,
    UserStatus status,
    LocalDateTime createdAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getRole(),
            user.getStatus(),
            user.getCreatedAt()
        );
    }
}
