package com.sparta.one_stop.global.security;


import com.sparta.one_stop.domain.auth.service.RedisTokenService;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTokenService redisTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

            String accessToken = jwtTokenProvider.resolveToken(request.getHeader("Authorization"));

            //  SecurityContext 중복 인증 방지 (필터 재진입, 포워딩 시 안전장치)
            if (accessToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 토큰 유효성 검증 및 Claims 파싱
                Claims claims = jwtTokenProvider.parseClaims(accessToken);

                // [개선 2] 라이브러리 예외 대신 CustomException 사용
                String jti = claims.getId();
                if (redisTokenService.isBlacklisted(jti)) {
                    log.debug("블랙리스트 처리된 토큰 접근 차단: jti={}", jti); // [개선 8] warn -> debug로 낮춰 로그 폭발 방지
                    throw new CustomException(ErrorCode.AUTH_009, "이미 로그아웃 처리된 토큰입니다.");
                }

                // 인증 객체 셋팅
                setAuthentication(claims, request);
            }

        // 예외가 발생했든 안 했든 필터 체인은 무조건 타도록 보장
        filterChain.doFilter(request, response);
    }

    private void setAuthentication(Claims claims, HttpServletRequest request) {
        Long userId = jwtTokenProvider.getUserId(claims);
        UserRole role = jwtTokenProvider.getRole(claims);

        AuthUser authUser = new AuthUser(userId, role);

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                authUser,
                null,
                authUser.authorities()
            );

        // IP, Session 정보 등 Audit Log에 디테일 셋팅
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("인증 완료: userId={}, role={}", userId, role);
    }
}
