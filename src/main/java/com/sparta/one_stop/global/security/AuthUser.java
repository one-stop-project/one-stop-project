package com.sparta.one_stop.global.security;

import com.sparta.one_stop.global.enums.user.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

public record AuthUser(Long userId, UserRole role) {

    /** Spring Security용 권한 정보 제공 */
    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public boolean isBuyer() {
        return role == UserRole.BUYER;
    }

    public boolean isSeller() {
        return role == UserRole.SELLER;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN;
    }

    public boolean isSuperAdmin() {
        return role == UserRole.SUPER_ADMIN;
    }
}
