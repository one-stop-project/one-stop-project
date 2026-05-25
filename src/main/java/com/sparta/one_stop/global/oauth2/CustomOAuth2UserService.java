package com.sparta.one_stop.global.oauth2;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * ═══════════════════════════════════════════════════════════
 *  Spring Security OAuth2 흐름
 * ═══════════════════════════════════════════════════════════
 *
 *  1. 카카오 인가 코드 수신 (자동)
 *  2. 카카오 토큰 교환 (자동)
 *  3. 카카오 /v2/user/me 호출 (super.loadUser)
 *  4. Provider별 OAuth2UserInfo 변환 (이 클래스)
 *  5. 우리 DB 사용자 매칭/생성 (OAuth2MemberService)
 *  6. CustomOAuth2User 반환 → SecurityContext 저장
 *  7. OAuth2SuccessHandler가 JWT 발급
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuth2MemberService oauth2MemberService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. 외부 OAuth2 Provider에서 사용자 정보 받기
        OAuth2User oauth2User = super.loadUser(userRequest);
        String providerName = userRequest.getClientRegistration().getRegistrationId();

        try {
            // 2. Provider별 매핑
            OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(
                providerName, oauth2User.getAttributes()
            );

            // 3. 이메일 검증
            String email = userInfo.getEmail();
            if (email == null || email.isBlank()) {
                throw new CustomException(ErrorCode.AUTH_018,
                    "OAuth2 제공자로부터 이메일을 받지 못했습니다. 동의 항목을 확인해주세요.");
            }

            // 4. 사용자 매칭 또는 신규 가입 (별도 서비스 위임)
            User user = oauth2MemberService.findOrCreateUser(userInfo);

            return new CustomOAuth2User(user, oauth2User.getAttributes(), userInfo);

        } catch (CustomException e) {
            // ★ 이메일 평문 로깅 제거 (CodeRabbit Major)
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
}
