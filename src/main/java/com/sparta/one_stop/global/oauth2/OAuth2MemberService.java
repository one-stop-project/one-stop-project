package com.sparta.one_stop.global.oauth2;

import com.sparta.one_stop.domain.admin.security.SuspensionPolicyService;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * OAuth2 사용자 매칭/생성 흐름 조율
 *
 * ═══════════════════════════════════════════════════════════
 *  v1 대비 핵심 변경 (CodeRabbit Major)
 * ═══════════════════════════════════════════════════════════
 *
 *  1. self-invocation 제거
 *     - v1: createNewOAuth2User를 같은 클래스에서 호출 → REQUIRES_NEW 무효
 *     - v2: OAuth2NewUserCreator 별도 클래스 주입 → 프록시 경유 보장
 *
 *  2. 이메일 평문 로깅 제거 (CodeRabbit Major)
 *     - v1: log.warn("... email={}", email)
 *     - v2: provider만 로깅
 *
 *  3. 더 정확한 에러 처리
 *     - 이메일 충돌과 일반 오류를 분리된 에러코드로 응답
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2MemberService {

    private final UserRepository userRepository;
    private final OAuth2NewUserCreator newUserCreator;
    private final SuspensionPolicyService suspensionPolicyService;

    @Transactional
    public User findOrCreateUser(OAuth2UserInfo userInfo) {
        // 1. provider + providerId로 기존 사용자 조회 (가장 빠른 경로)
        Optional<User> existing = userRepository.findByProviderAndProviderId(
            userInfo.getProvider(), userInfo.getProviderId()
        );

        if (existing.isPresent()) {
            User user = existing.get();
            suspensionPolicyService.validateOrRelease(user);
            user.verifyActive();
            user.recordLogin();
            log.debug("[OAuth2] 기존 사용자 로그인: userId={}, provider={}",
                user.getId(), userInfo.getProvider());
            return user;
        }

        // 2. 이메일 충돌 검사 (일반 가입 존재 시)
        if (userRepository.existsByEmail(userInfo.getEmail())) {
            // ★ 이메일 평문 로깅 제거 (CodeRabbit Major)
            log.warn("[OAuth2] 이메일 충돌 — 일반 가입 존재: provider={}, providerId={}",
                userInfo.getProvider(), userInfo.getProviderId());
            throw new CustomException(ErrorCode.AUTH_019);
        }

        // 3. 신규 가입 — 별도 트랜잭션 (REQUIRES_NEW 진짜 보장)
        return newUserCreator.create(userInfo);
    }
}
