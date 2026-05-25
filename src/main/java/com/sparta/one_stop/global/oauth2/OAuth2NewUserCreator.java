package com.sparta.one_stop.global.oauth2;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2NewUserCreator {

    private final UserRepository userRepository;

    /**
     * 신규 OAuth2 사용자 생성 — 별도 트랜잭션
     *
     * Race Condition 방어:
     *   - 같은 OAuth2 계정 동시 로그인 시 1건만 성공
     *   - 나머지는 DuplicateKeyException → 재조회로 복구
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User create(OAuth2UserInfo userInfo) {
        User newUser = User.builder()
            .email(userInfo.getEmail())
            .password("{noop}" + UUID.randomUUID())
            .name(userInfo.getName() != null ? userInfo.getName() : "소셜회원")
            .role(UserRole.BUYER)
            .build();

        newUser.linkOAuth2(userInfo.getProvider(), userInfo.getProviderId());

        try {
            User saved = userRepository.saveAndFlush(newUser);
            log.info("[OAuth2] 신규 사용자 가입: userId={}, provider={}",
                saved.getId(), userInfo.getProvider());
            return saved;

        } catch (DuplicateKeyException e) {
            // ★ 1. UNIQUE 제약 위반 — 동시 가입 충돌 (정상 시나리오)
            log.warn("[OAuth2] 동시 가입 충돌 — provider={}, providerId={}",
                userInfo.getProvider(), userInfo.getProviderId());

            return userRepository.findByProviderAndProviderId(
                userInfo.getProvider(), userInfo.getProviderId()
            ).orElseThrow(() -> new CustomException(ErrorCode.AUTH_018,
                "OAuth2 사용자 생성 중 알 수 없는 오류"));

        } catch (DataIntegrityViolationException e) {
            // ★ 2. 그 외 무결성 위반 — 진짜 오류 (NOT NULL, FK, CHECK 등)
            log.error("[OAuth2] 무결성 위반 — provider={}, providerId={}",
                userInfo.getProvider(), userInfo.getProviderId(), e);
            throw new CustomException(ErrorCode.AUTH_018,
                "OAuth2 사용자 생성 실패");
        }
    }
}
