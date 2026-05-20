package com.sparta.one_stop.global.security;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.domain.auth.service.RedisTokenService;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.response.ErrorResponse;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtExceptionFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTokenService redisTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException e) {
            log.debug("만료된 JWT: uri={}", request.getRequestURI());
            writeErrorResponse(request, response, ErrorCode.AUTH_008, e.getMessage());
        } catch (CustomException e) {
            log.warn("JWT 비즈니스 예외: code={}", e.getErrorCode().getCode());
            writeErrorResponse(request, response, e.getErrorCode(), e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("유효하지 않은 JWT: uri={}, message={}", request.getRequestURI(), e.getMessage());
            writeErrorResponse(request, response, ErrorCode.AUTH_009, e.getMessage());
        }
    }

    private void writeErrorResponse(HttpServletRequest request, HttpServletResponse response,
                                    ErrorCode errorCode, String detail) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
            ErrorResponse.of(errorCode, detail, request.getRequestURI())));
    }
}
