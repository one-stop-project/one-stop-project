package com.sparta.one_stop.global.oauth2;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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

        } catch (DataIntegrityViolationException e) {
            // UNIQUE 제약 위반 — 동시 가입 충돌 (정상 시나리오).
            // JPA는 보통 DataIntegrityViolationException으로 던지므로 둘 다 잡아
            // 기존 가입자 복구를 시도한다.
            log.warn("[OAuth2] 가입 충돌(복구 시도) — provider={}, providerId={}",
                userInfo.getProvider(), userInfo.getProviderId());

            return userRepository.findByProviderAndProviderId(
                userInfo.getProvider(), userInfo.getProviderId()
            ).orElseThrow(() -> {
                // 복구도 실패 = UNIQUE 충돌이 아닌 진짜 무결성 오류 (NOT NULL/FK 등)
                log.error("[OAuth2] 무결성 위반(복구 실패) — provider={}, providerId={}",
                    userInfo.getProvider(), userInfo.getProviderId(), e);
                return new CustomException(ErrorCode.AUTH_018, "OAuth2 사용자 생성 실패");
            });
        }
    }
}
