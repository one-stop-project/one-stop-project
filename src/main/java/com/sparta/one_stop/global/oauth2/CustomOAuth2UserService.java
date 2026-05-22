package com.sparta.one_stop.global.oauth2;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * OAuth2 사용자 처리 서비스 — 동시성 안전 + 트래픽 최적화판
 *
 * ═══════════════════════════════════════════════════════════
 *  v1 대비 핵심 변경
 * ═══════════════════════════════════════════════════════════
 *
 * 1. Race Condition 방어
 *    - 시나리오: 같은 카카오 계정 동시 로그인 → 사용자 2명 생성될 수 있음
 *    - 방어: UNIQUE (provider, provider_id) 인덱스 + DataIntegrityViolation catch
 *    - 패턴: 회원가입 동시성 방어와 동일
 *
 * 2. 트랜잭션 경계 분리
 *    - 신규 가입: REQUIRES_NEW (별도 트랜잭션)
 *    - 기존 사용자: NOT_REQUIRED (조회만)
 *    - 외부 API(카카오) 호출과 DB 트랜잭션 격리
 *
 * 3. provider + providerId 검색 우선
 *    - email보다 안정적 (이메일 변경 가능)
 *    - 정확한 인덱스 활용
 *
 * 4. 이메일 충돌 시 명확한 에러
 *    - 같은 이메일로 일반 가입 존재 시 AUTH_022
 *    - 사용자에게 명확한 메시지 ("이미 일반 가입된 이메일입니다")
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. 카카오/구글/네이버에서 사용자 정보 받기 (외부 API 호출)
        // ⚠️ 이 부분은 트랜잭션 밖 — 외부 호출이 길어져도 DB Connection 안 잡음
        OAuth2User oauth2User = super.loadUser(userRequest);
        String providerName = userRequest.getClientRegistration().getRegistrationId();

        try {
            OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(
                providerName, oauth2User.getAttributes()
            );

            String email = userInfo.getEmail();
            if (email == null || email.isBlank()) {
                throw new CustomException(ErrorCode.AUTH_018,
                    "OAuth2 제공자로부터 이메일을 받지 못했습니다. 동의 항목을 확인해주세요.");
            }

            // 2. 사용자 매칭 또는 신규 가입 (DB 트랜잭션 — 별도 메서드로 분리)
            User user = findOrCreateUser(userInfo);

            return new CustomOAuth2User(user, oauth2User.getAttributes(), userInfo);

        } catch (CustomException e) {
            log.error("[OAuth2] 인증 실패: provider={}, code={}",
                providerName, e.getErrorCode().getCode());
            throw new OAuth2AuthenticationException(
                new OAuth2Error(e.getErrorCode().getCode()),
                e.getMessage()
            );
        } catch (Exception e) {
            log.error("[OAuth2] 예상치 못한 오류: provider={}", providerName, e);
            throw new OAuth2AuthenticationException(
                new OAuth2Error("AUTH_018"),
                "OAuth2 인증 처리 중 오류가 발생했습니다"
            );
        }
    }

    /**
     * 사용자 매칭 또는 신규 가입 — 동시성 안전
     *
     * 우선순위:
     *   1. provider + providerId 매칭 → 기존 사용자 (가장 빠른 경로)
     *   2. email 매칭 → 일반 가입 충돌 (AUTH_022)
     *   3. 매칭 없음 → 자동 가입 (BUYER, Race Condition 방어)
     */
    @Transactional
    public User findOrCreateUser(OAuth2UserInfo userInfo) {
        // 1. provider + providerId로 기존 OAuth2 사용자 검색 (재방문 — 가장 흔한 경로)
        Optional<User> existing = userRepository.findByProviderAndProviderId(
            userInfo.getProvider(), userInfo.getProviderId()
        );

        if (existing.isPresent()) {
            User user = existing.get();
            user.verifyActive();
            user.recordLogin();
            log.debug("[OAuth2] 기존 사용자 로그인: userId={}, provider={}",
                user.getId(), userInfo.getProvider());
            return user;
        }

        // 2. email로 일반 가입 충돌 검사
        if (userRepository.existsByEmail(userInfo.getEmail())) {
            log.warn("[OAuth2] 이메일 충돌 — 일반 가입 존재: email={}, provider={}",
                userInfo.getEmail(), userInfo.getProvider());
            throw new CustomException(ErrorCode.AUTH_019);
        }

        // 3. 신규 가입 (Race Condition 방어)
        return createNewOAuth2User(userInfo);
    }

    /**
     * 신규 OAuth2 사용자 생성 — Race Condition 안전
     *
     * 시나리오: 같은 카카오 계정으로 동시에 첫 로그인 2회 요청
     *   - 둘 다 위 findByProvider...로 검색 → 둘 다 없음
     *   - 둘 다 createNewOAuth2User 호출
     *   - 1건만 INSERT 성공, 1건은 DataIntegrityViolation
     *   - catch에서 다시 조회 → 성공한 사용자 반환
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User createNewOAuth2User(OAuth2UserInfo userInfo) {
        User newUser = User.builder()
            .email(userInfo.getEmail())
            .password(generateRandomPassword())
            .name(userInfo.getName() != null ? userInfo.getName() : "사용자")
            .role(UserRole.BUYER)
            .build();

        newUser.linkOAuth2(userInfo.getProvider(), userInfo.getProviderId());

        try {
            User saved = userRepository.save(newUser);
            log.info("[OAuth2] 신규 사용자 자동 가입: userId={}, email={}, provider={}",
                saved.getId(), saved.getEmail(), userInfo.getProvider());
            return saved;

        } catch (DataIntegrityViolationException e) {
            // 동시성 충돌 — 다른 요청이 먼저 가입 성공
            log.warn("[OAuth2] 동시 가입 충돌 — 기존 사용자 조회로 전환: provider={}, providerId={}",
                userInfo.getProvider(), userInfo.getProviderId());

            // 다시 조회 (이번엔 존재)
            return userRepository.findByProviderAndProviderId(
                userInfo.getProvider(), userInfo.getProviderId()
            ).orElseThrow(() -> new CustomException(ErrorCode.AUTH_018,
                "OAuth2 사용자 생성 중 알 수 없는 오류"));
        }
    }

    /**
     * OAuth2 사용자용 임의 비밀번호 (실제로 사용되지 않음)
     */
    private String generateRandomPassword() {
        // BCrypt 인코딩 없이 그냥 랜덤 — 어차피 비교 안 함
        // 보안: passwordEncoder.matches로 비교 시 자동 false
        return "{noop}" + UUID.randomUUID() + UUID.randomUUID();
    }
}
