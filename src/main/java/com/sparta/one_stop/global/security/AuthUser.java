package com.sparta.one_stop.global.security;

import com.sparta.one_stop.global.enums.user.UserRole;

public record AuthUser(Long userId, UserRole role) {
}
