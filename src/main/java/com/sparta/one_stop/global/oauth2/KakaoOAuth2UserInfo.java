package com.sparta.one_stop.global.oauth2;

import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;

import java.util.Map;

public class KakaoOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;
    private final Map<String, Object> kakaoAccount;
    private final Map<String, Object> profile;

    @SuppressWarnings("unchecked")
    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            throw new CustomException(ErrorCode.AUTH_018,
                "카카오로부터 사용자 정보를 받지 못했습니다");
        }

        this.attributes = attributes;
        this.kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");

        // ★ NPE 방어 — kakaoAccount null 체크
        this.profile = (kakaoAccount != null)
            ? (Map<String, Object>) kakaoAccount.get("profile")
            : null;
    }

    @Override
    public String getProviderId() {
        Object id = attributes.get("id");

        // ★ "null" 문자열 저장 방지
        if (id == null) {
            throw new CustomException(ErrorCode.AUTH_018,
                "카카오 사용자 ID를 받지 못했습니다");
        }
        return String.valueOf(id);
    }

    @Override
    public String getProvider() {
        return "kakao";
    }

    @Override
    public String getEmail() {
        if (kakaoAccount == null) {
            return null;
        }
        Object email = kakaoAccount.get("email");
        return email != null ? email.toString() : null;
    }

    @Override
    public String getName() {
        if (profile == null) {
            return null;
        }
        Object nickname = profile.get("nickname");
        return nickname != null ? nickname.toString() : null;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    // ═══════════════════════════════════════════════════════════
    //  카카오 전용 추가 검증 (선택사항)
    // ═══════════════════════════════════════════════════════════

    /**
     * 이메일 인증 완료 여부
     * false면 동의 안 했거나 미인증 이메일
     */
    public boolean isEmailVerified() {
        if (kakaoAccount == null) return false;
        Object verified = kakaoAccount.get("is_email_verified");
        return Boolean.TRUE.equals(verified);
    }

    /**
     * 이메일 유효성 (카카오 측 검증)
     */
    public boolean isEmailValid() {
        if (kakaoAccount == null) return false;
        Object valid = kakaoAccount.get("is_email_valid");
        return Boolean.TRUE.equals(valid);
    }
}
