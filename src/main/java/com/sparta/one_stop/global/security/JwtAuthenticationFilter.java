package com.sparta.one_stop.global.security;


import com.sparta.one_stop.global.enums.user.UserRole;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    // JWT 인증 필터
    // 1. Bearer 토큰 추출 -> Claims 파싱 -> SecurityContext 세팅
    // 2. JWT 예외(만료/변조)는 JwtExceptionFilter에서 catch하여 분리 응답
    // 3. email 없이 userId + role만으로 인증 정보 구성

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException
    {
        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            //예외 발생 시 JwtExceptionFilter가 catch
            Claims claims = jwtTokenProvider.parseClaims(token);

            Long userId = jwtTokenProvider.getUserId(claims);
            UserRole role = jwtTokenProvider.getRole(claims);

            //AuthUser
            AuthUser authUser = new AuthUser(userId, role);

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    authUser,
                    null,
                    authUser.authorities());

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("인증 완료 : userId={}, role={}", userId, role);
        }

        filterChain.doFilter(request, response);
    }


    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearer) && bearer.startsWith(BEARER_PREFIX)) {
            return bearer.substring(BEARER_PREFIX.length());
        }
        return null;
    }

}
