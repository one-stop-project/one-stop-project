package com.sparta.one_stop.global.security;

import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 토큰 생성 / 검증 컴포넌트
 * Access Token: 15분
 * Refresh Token: 14일
 */
@Slf4j
@Component
public class JwtTokenProvider {

    // Access Token 유효시간: 15분
    private static final long ACCESS_TOKEN_EXPIRE = 1000L * 60 * 15;
    // Refresh Token 유효시간: 14일
    private static final long REFRESH_TOKEN_EXPIRE = 1000L * 60 * 60 * 24 * 14;
    @Value("${jwt.secret.key}")
    private String secretKeyString;
    private SecretKey secretKey;

    @PostConstruct
    protected void init() {
        // Base64 인코딩된 키를 SecretKey로 변환
        byte[] keyBytes = Base64.getDecoder().decode(secretKeyString);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Access Token 생성
     * Payload: userId, email, role
     */
    public String createAccessToken(Long userId, String email, String role) {
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("email", email)
            .claim("role", role)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRE))
            .signWith(secretKey)
            .compact();
    }

    /**
     * Refresh Token 생성
     * Payload: userId만 포함 (최소한의 정보)
     */
    public String createRefreshToken(Long userId) {
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRE))
            .signWith(secretKey)
            .compact();
    }

    /**
     * 토큰에서 Claims 추출
     */
    public Claims getClaims(String token) {
        try {
            return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.AUTH_008);
        } catch (JwtException e) {
            throw new CustomException(ErrorCode.AUTH_009);
        }
    }

    /**
     * 토큰에서 userId 추출
     */
    public Long getUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    /**
     * 토큰에서 role 추출
     */
    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    /**
     * 토큰 유효성 검증
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (CustomException e) {
            return false;
        }
    }
}
