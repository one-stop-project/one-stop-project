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

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 토큰 생성 / 검증 컴포넌트
 * Access Token: 15분
 * Refresh Token: 7일
 */
@Slf4j
@Component
public class JwtTokenProvider {

    public static final String BEARER_PREFIX = "Bearer ";
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
    public String createAccessToken(Long userId, UserRole role, int tokenVersion) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + accessTokenExpiry.toMillis());

        return Jwts.builder()
            .id(UUID.randomUUID().toString())            // JTI
            .subject(String.valueOf(userId))             // userId
            .claim("role", role.name())            // role
            .claim("ver", tokenVersion)                  // tokenVersion (user-level 무효화)
            .issuedAt(now)
            .expiration(expire)
            .signWith(secretKey, Jwts.SIG.HS256)         //  알고리즘 명시
            .compact();
    }

    /**
     * 토큰 버전(ver) 추출 — user-level 무효화 검증용
     * @return ver claim, 없으면 0 (구 토큰 호환)
     */
    public int getTokenVersion(Claims claims) {
        Integer ver = claims.get("ver", Integer.class);
        return ver != null ? ver : 0;
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
            // Enum 매칭 실패 시 500 에러가 나지 않도록 커스텀 예외로 변환
            throw new CustomException(ErrorCode.AUTH_009, "유효하지 않은 권한 정보입니다.");
        }
    }

    /**
     * 토큰 발급시각(iat) 추출 — epoch 초
     *
     * <p>user-level 토큰 무효화(iat-cutoff) 검증용.
     * @return iat(epoch seconds), 없으면 0
     */
    public long getIssuedAtEpochSeconds(Claims claims) {
        java.util.Date issuedAt = claims.getIssuedAt();
        return issuedAt != null ? issuedAt.getTime() / 1000 : 0L;
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
            // 버전 0.12.x 문법에 맞게 수정 및 key -> secretKey 변경
            Date expiration = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();

            long now = new Date().getTime();
            long remainTime = expiration.getTime() - now;

            return remainTime > 0 ? remainTime / 1000 : 0; // 남은 시간을 초(Seconds) 단위로 반환
        } catch (ExpiredJwtException e) {
            return 0; // 이미 만료된 토큰은 0을 반환 (블랙리스트에 넣을 필요 없음)
        } catch (Exception e) {
            return 0; // 파싱 실패 시 예외 처리
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

    /**
     * 토큰에서 userId 추출 (만료된 토큰도 허용 — logout용)
     *
     * 서명이 유효하면 만료 여부와 무관하게 subject(userId)를 반환한다.
     * 로그아웃처럼 "만료된 AT로도 정리해야 하는" 경우에 사용.
     *
     * @return userId, 파싱 불가 시 null
     */
    public Long getUserIdAllowExpired(String token) {
        if (token == null) {
            return null;
        }
        try {
            return Long.parseLong(parseClaims(token).getSubject());
        } catch (ExpiredJwtException e) {
            // 만료됐어도 subject는 읽을 수 있음
            return Long.parseLong(e.getClaims().getSubject());
        } catch (Exception e) {
            log.debug("logout용 userId 추출 실패 (무시): {}", e.getMessage());
            return null;
        }
    }


}



