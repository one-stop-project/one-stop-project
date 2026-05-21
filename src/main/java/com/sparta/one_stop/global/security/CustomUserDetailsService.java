package com.sparta.one_stop.global.security;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 인증 시점에 사용자 정보를 DB에서 로드
 *
 * 호출 시점:
 *   - AuthenticationManager.authenticate() 호출 시 (로그인 처리)
 *   - 그 외 매 요청에서는 호출되지 않음 (JwtAuthenticationFilter가 AuthUser 직접 세팅)
 *
 * 반환:
 *   - CustomUserDetails (Spring Security 표준 UserDetails 구현체)
 *
 * 비교: AuthUser는 매 요청마다 JWT 클레임에서 직접 생성됨 (DB 조회 X)
 */

// 현재는 dead코드이나 추후 OAuth2랑 연계 예정
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new CustomException(ErrorCode.AUTH_004));
        return new CustomUserDetails(user);
    }
}

