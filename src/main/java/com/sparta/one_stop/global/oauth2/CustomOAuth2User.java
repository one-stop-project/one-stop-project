package com.sparta.one_stop.global.oauth2;

import com.sparta.one_stop.domain.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Getter
public class CustomOAuth2User implements OAuth2User {

    private final User user;
    private final Map<String, Object> attributes;
    private final OAuth2UserInfo userInfo;

    public CustomOAuth2User(User user, Map<String, Object> attributes, OAuth2UserInfo userInfo) {
        this.user = user;
        this.attributes = attributes;
        this.userInfo = userInfo;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // User 엔티티의 Role을 Spring Security 권한으로 변환
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getName() {
        return String.valueOf(user.getId()); // 고유 식별자로 DB PK 값을 사용
    }
}
