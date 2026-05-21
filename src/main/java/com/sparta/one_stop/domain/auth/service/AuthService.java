package com.sparta.one_stop.domain.auth.service;

import com.sparta.one_stop.domain.auth.dto.request.LoginRequest;
import com.sparta.one_stop.domain.auth.dto.response.LoginResponse;

import com.sparta.one_stop.domain.auth.dto.result.LoginResult;
import com.sparta.one_stop.domain.auth.dto.result.RefreshResult;
import com.sparta.one_stop.domain.auth.dto.request.SignUpRequest;
import com.sparta.one_stop.domain.auth.dto.response.SignUpResponse;
import com.sparta.one_stop.domain.auth.dto.request.TokenRefreshRequest;
import com.sparta.one_stop.domain.auth.dto.response.TokenRefreshResponse;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTokenService redisTokenService;

    //  허용된 회원가입 Role 명시적 관리
    private static final Set<UserRole> ALLOWED_SIGNUP_ROLES = Set.of(UserRole.BUYER, UserRole.SELLER);

    private String dummyHash;

    // 타이밍 공격 방어용 Dummy Hash를 런타임에 동적 생성 (에러 방지)
    @PostConstruct
    public void init() {
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  POST /api/auth/signup — 회원가입
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Transactional
    public SignUpResponse signup(SignUpRequest request) {
        if (!ALLOWED_SIGNUP_ROLES.contains(request.role())) {
            throw new CustomException(ErrorCode.AUTH_011, "허용되지 않은 가입 권한입니다.");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.AUTH_002);
        }

        if (request.role() == UserRole.SELLER) {
            validateSellerFields(request);
        }

        User user = User.builder()
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .name(request.name())
            .phone(request.phone())
            .address(request.address())
            .role(request.role())
            .build();

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("회원가입 동시성 충돌 - 이메일 중복: {}", maskEmail(request.email()));
            throw new CustomException(ErrorCode.AUTH_002);
        }

        if (request.role() == UserRole.SELLER) {
            Seller seller = Seller.builder()
                .user(user)
                .shopName(request.shopName())
                .businessNumber(request.businessNumber())
                .bankAccount(request.bankAccount())
                .build();
            sellerRepository.save(seller);
        }

        return SignUpResponse.from(user);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  POST /api/auth/login — 로그인
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Transactional(readOnly = true)
    public LoginResult login(LoginRequest request, String deviceId) {
        User user = userRepository.findByEmail(request.email()).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            log.info("로그인 실패 - 계정 없음: {}", maskEmail(request.email()));
            throw new CustomException(ErrorCode.AUTH_004);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            log.info("로그인 실패 - 비밀번호 불일치: userId={}", user.getId());
            throw new CustomException(ErrorCode.AUTH_004);
        }

        validateUserStatus(user);

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), deviceId);

        // 다중 기기(deviceId) 식별자를 포함하여 저장
        redisTokenService.saveRefreshToken(
            user.getId(), deviceId, refreshToken,
            jwtTokenProvider.getRefreshTokenExpirySeconds());

        LoginResponse response = LoginResponse.of(
            accessToken,
            jwtTokenProvider.getAccessTokenExpirySeconds(),
            user
        );

        return new LoginResult(response, refreshToken);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  POST /api/auth/refresh — 토큰 재발급
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Transactional(readOnly = true)
    public RefreshResult refresh(TokenRefreshRequest request, String deviceId) {
        String oldRefreshToken = request.refreshToken();
        Claims claims;

        try {
            claims = jwtTokenProvider.parseClaims(oldRefreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.AUTH_010);
        }

        // 토큰-쿠키 deviceId 일치 검증
        String tokenDeviceId = claims.get("deviceId", String.class);
        if (tokenDeviceId == null || !tokenDeviceId.equals(deviceId)) {
            log.warn("RT-Cookie deviceId 불일치 (탈취 의심): tokenDeviceId={}, cookieDeviceId={}",
                tokenDeviceId, deviceId);
            throw new CustomException(ErrorCode.AUTH_010);
        }

        Long userId = jwtTokenProvider.getUserId(claims);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));
        validateUserStatus(user);

        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId(), deviceId);  // ★ deviceId 전달

        // Lua Script CAS를 이용한 원자적 갱신 검증
        boolean isRotated = redisTokenService.rotateRefreshTokenCAS(
            userId, deviceId, oldRefreshToken, newRefreshToken, jwtTokenProvider.getRefreshTokenExpirySeconds()
        );

        if (!isRotated) {
            log.warn("RT 원자적 갱신 실패 (동시성 충돌 or 이미 갱신됨/탈취 의심): userId={}, deviceId={}", userId, deviceId);
            throw new CustomException(ErrorCode.AUTH_010);
        }

        // DTO에는 RT 없이, 별도로 newRefreshToken 반환
        TokenRefreshResponse response = TokenRefreshResponse.of(
            newAccessToken,
            jwtTokenProvider.getAccessTokenExpirySeconds()
        );

        return new RefreshResult(response, newRefreshToken);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  POST /api/auth/logout — 로그아웃
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    public void logout(Long userId, String deviceId, String accessToken) {
        redisTokenService.deleteRefreshToken(userId, deviceId);

        if (accessToken != null) {
            try {
                String jti = jwtTokenProvider.getJti(accessToken);
                long expiration = jwtTokenProvider.getExpiration(accessToken);

                if (expiration > 0) {  // ← 가드 추가
                    redisTokenService.addToBlacklist(jti, expiration);
                } else {
                    log.debug("이미 만료된 토큰은 블랙리스트 추가 생략: jti={}", jti);
                }

            } catch (Exception e) {
                log.error("로그아웃 부분 실패 - JTI 파싱 오류: userId={}", userId, e);
            }
        }
        log.info("로그아웃 완료: userId={}, deviceId={}", userId, deviceId);
    }

    // ── Private ── : 검증 및 이메일 마스킹 로직
    private void validateUserStatus(User user) {
        if (user.isSuspended()) throw new CustomException(ErrorCode.AUTH_005);
        if (!user.isActive()) throw new CustomException(ErrorCode.AUTH_006);
    }

    private void validateSellerFields(SignUpRequest request) {
        // 프로젝트 ErrorCode Enum에 COMMON_001이 있는지 확인하시고 없으면 적절한 코드로 변경해주세요.
        if (request.shopName() == null || request.shopName().isBlank()) {
            throw new CustomException(ErrorCode.COMMON_001, "판매자 상호명은 필수입니다");
        }
        if (request.businessNumber() == null || request.businessNumber().isBlank()) {
            throw new CustomException(ErrorCode.COMMON_001, "사업자 등록번호는 필수입니다");
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;

        int atIndex = email.indexOf("@");
        String id = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (id.length() <= 2) {
            return "*".repeat(id.length()) + domain;
        }
        return id.substring(0, 2) + "*".repeat(id.length() - 2) + domain;
    }
}
