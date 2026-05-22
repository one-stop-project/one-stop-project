package com.sparta.one_stop.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

        @Override
        public void handle(HttpServletRequest request,
                           HttpServletResponse response,
                           AccessDeniedException accessDeniedException) throws IOException {

            log.warn("권한 없는 접근 시도: uri={}, message={}", request.getRequestURI(), accessDeniedException.getMessage());

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                ErrorResponse.of(
                    ErrorCode.AUTH_011,
                    ErrorCode.AUTH_011.getMessage(),
                    request.getRequestURI()
                )
            ));
    }

}


