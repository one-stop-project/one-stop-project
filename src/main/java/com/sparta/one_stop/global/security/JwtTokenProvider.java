package com.sparta.one_stop.global.security;


import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.JwtException;

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
     * JTI(고유 ID)포함
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
            .signWith(secretKey, Jwts.SIG.HS256)         //  알고리즘 명시
            .compact();
    }

    /**
     * Refresh Token 생성
     * Payload: userId + JTI만
     */
    public String createRefreshToken(Long userId, String deviceId) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + refreshTokenExpiry.toMillis());

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(String.valueOf(userId))
            .claim("deviceId", deviceId)  // ← 추가
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
     * 토큰에서 role 추출 시 Enum 예외 방어
     */
    public UserRole getRole(Claims claims) {
        try {
            return UserRole.valueOf(claims.get("role", String.class));
        } catch (IllegalArgumentException | NullPointerException e) {
            // [개선 9] Enum 매칭 실패 시 500 에러가 나지 않도록 커스텀 예외로 변환
            throw new CustomException(ErrorCode.AUTH_010, "유효하지 않은 권한 정보입니다.");
        }
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

    /**
     * 토큰의 남은 만료 시간(초)을 계산합니다. (블랙리스트 저장용 TTL)
     */
    public long getExpirationSeconds(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            // 현재 시간(UTC 기준)과 비교가 필요할 수 있습니다.
            // 현재는 서버 시간을 사용하고 계신데, 이는 아주 적절합니다.
            long now = System.currentTimeMillis();
            long remainTime = claims.getExpiration().getTime() - now;

            return remainTime > 0 ? remainTime / 1000 : 0;
        } catch (JwtException e) { // ExpiredJwtException 포함한 모든 JWT 관련 예외를 포괄
            return 0;
        }
    }

    /**
     * 토큰에서 고유 식별자(JTI)를 추출합니다. (String 타입의 토큰을 받을 때 사용)
     */
    public String getJti(String token) {
        try {
            return parseClaims(token).getId();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getId();  // 만료된 토큰의 JTI도 추출 가능
        }
    }

    public static final String BEARER_PREFIX = "Bearer ";

    /**
     * 헤더에서 순수 토큰만 추출합니다.
     */
    public String resolveToken(String bearerToken) {
        //  trim() 적용하여 안전성 극대화
        if (bearerToken != null && bearerToken.trim().startsWith(BEARER_PREFIX)) {
            return bearerToken.trim().substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

}


