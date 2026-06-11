package com.sparta.one_stop.global.security;


import com.sparta.one_stop.domain.auth.service.AuthQueryService;
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
    private final AuthQueryService authQueryService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        String accessToken = jwtTokenProvider.resolveToken(request.getHeader("Authorization"));

        //  SecurityContext 중복 인증 방지 (필터 재진입, 포워딩 시 안전장치)
        if (accessToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // 토큰 유효성 검증 및 Claims 파싱
            Claims claims = jwtTokenProvider.parseClaims(accessToken);

            //  라이브러리 예외 대신 CustomException 사용
            String jti = claims.getId();
            if (redisTokenService.isBlacklisted(jti)) {
                log.debug("블랙리스트 처리된 토큰 접근 차단: jti={}", jti); // [개선 8] warn -> debug로 낮춰 로그 폭발 방지
                throw new CustomException(ErrorCode.AUTH_009, "이미 로그아웃 처리된 토큰입니다.");
            }

            Long userId = jwtTokenProvider.getUserId(claims);

            // user-level 토큰 무효화 검증 (비번변경/탈퇴/정지 시 cutoff 이전 AT 거부)
            long iat = jwtTokenProvider.getIssuedAtEpochSeconds(claims);
            if (redisTokenService.isTokenInvalidated(userId, iat)) {
                log.debug("무효화된 토큰 접근 차단 (cutoff 이전 발급): userId={}", userId);
                throw new CustomException(ErrorCode.AUTH_009, "보안 정책에 의해 만료된 토큰입니다. 다시 로그인해주세요.");
            }

            // tokenVersion 검증 (DB 영속 계층 — Redis 죽어도 무효화 유지)
            // 발급 시점 ver와 현재 ver(캐시)가 다르면 거부
            int tokenVersion = jwtTokenProvider.getTokenVersion(claims);
            authQueryService.verifyTokenVersion(userId, tokenVersion);

            // 활성 상태 검증(정지/탈퇴 실시간 반영)
            authQueryService.verifyActiveByCache(userId);

            // 인증 객체 셋팅
            setAuthentication(claims, userId, request);
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(Claims claims, Long userId, HttpServletRequest request) {
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
