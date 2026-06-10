package com.sparta.one_stop.global.security;


import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.user.UserRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
// 현재는 dead코드이나 추후 OAuth2랑 연계 예정
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String email;   // DB에서만 Setting, JWT 생성자에서는 null
    private final String password;
    private final UserRole role;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;
    private final AuthUser authUser;

    public CustomUserDetails(User user) {
        this.authUser = new AuthUser(user.getId(), user.getEmail(), user.getRole());
        this.userId = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.role = user.getRole();
        this.active = user.isActive();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    // JWT 토큰 기반 생성 - Filter에서 매 요청마다 사용(DB조회 없음)
    // AccessToken payload에 userId + role만으로 생성
    // email 필요시 userId로 DB or 캐시 조회

    public AuthUser getAuthUser() { return authUser; }

    @Override
    public String getUsername() {
        return String.valueOf(userId);
    }

    @Override
    public String getPassword() {return password;}

    @Override
    public boolean isAccountNonExpired() {return true;}

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {return true;}

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }



}
