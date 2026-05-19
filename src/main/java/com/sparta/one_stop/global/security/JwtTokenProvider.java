package com.sparta.one_stop.global.security;


import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 토큰 생성 / 검증 컴포넌트
 * Access Token: 15분
 * Refresh Token: 14일
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final int MINIMUM_KEY_LENGTH_BYTES = 32; // H256 = 256bit = 32bytes

    @Value("${jwt.secret.key}")
    private String secretKeyBase64;

    @Value("${jwt.access-token-expiry}")
    private Duration accessTokenExpiry;

    @DurationUnit(ChronoUnit.SECONDS)
    @Value("${jwt.refresh-token-expiry}")
    private Duration refreshTokenExpiry;

    private SecretKey secretKey;

    @PostConstruct
    protected void init() {
        // Base64 인코딩된 키를 SecretKey로 변환
        byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);

        // [2-1] Secret Key 길이 검증 실시 - HS256 최소 256비트 필수여야함.
        if (keyBytes.length < MINIMUM_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                String.format("JWT Secret Key는 최소 %d바이트(256비트)여야 합니다. 현재: %d바이트",

                    MINIMUM_KEY_LENGTH_BYTES, keyBytes.length));
        }

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT 초기화 완료: 키 길이={}비트, AT만료={}, RT만료={}",
            keyBytes.length * 8, accessTokenExpiry, refreshTokenExpiry);
    }

    /**
     * Access Token 생성
     * Payload: userId + role (email 등 PII 제거)
     * [2-2] JTI(고유 ID)포함
     */
    public String createAccessToken(Long userId, UserRole role) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + accessTokenExpiry.toMillis());

        return Jwts.builder()
            .id(UUID.randomUUID().toString())            // JTI
            .subject(String.valueOf(userId))             // userId
            .claim("role", role.name())            // role
            .issuedAt(now)
            .expiration(expire)
            .signWith(secretKey, Jwts.SIG.HS256)         // [2-1] 알고리즘 명시
            .compact();
    }

    /**
     * Refresh Token 생성
     * Payload: userId + JTI만
     */
    public String createRefreshToken(Long userId) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + refreshTokenExpiry.toMillis());

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(String.valueOf(userId))
            .issuedAt(now)
            .expiration(expire)
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact();
    }

    /**
     * 토큰 파싱 - 만료/변조를 구분하여 예외 던짐
     * Filter에서 catch 후 AUTH 008 / AUTH 009 분리
     */
    public Claims parseClaims(String token) {
            return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        }


    /**
     * 토큰에서 userId 추출
     */
    public Long getUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 토큰에서 role 추출
     */
    public UserRole getRole(Claims claims) {
        return UserRole.valueOf(claims.get("role", String.class));
    }

    /**
     * 토큰에서 JTI 추출
     */
    public String getJti(Claims claims) {
        return claims.getId();
    }

    /**
     * AT 만료 시간 (초 단위) - 블랙리스트 TTL 산정용
     */
    public long getRemainingExpiration(Claims claims) {
        return claims.getExpiration().getTime() - System.currentTimeMillis();
    }

    /**
     * AT 만료 시간 (초 단위) - 로그인 응답의 expiresIn 필드용
     */
    public long getAccessTokenExpirySeconds() {
        return accessTokenExpiry.toSeconds();
    }

    /**
     * RT 만료 시간 (초 단위) — Redis TTL 설정용
     */
    public long getRefreshTokenExpirySeconds() {
        return refreshTokenExpiry.toSeconds();
    }
}
