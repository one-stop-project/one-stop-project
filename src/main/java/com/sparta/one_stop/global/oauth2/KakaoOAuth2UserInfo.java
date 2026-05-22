package com.sparta.one_stop.global.oauth2;

import java.util.Map;

public class KakaoOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;
    private final Map<String, Object> kakaoAccount;
    private final Map<String, Object> profile;

    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
        this.kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        this.profile = (Map<String, Object>) kakaoAccount.get("profile");
    }

    @Override
    public String getProviderId() {
        return String.valueOf(attributes.get("id"));
    }

    @Override
    public String getProvider() {
        return "kakao";
    }

    @Override
    public String getEmail() {
        return kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
    }

    @Override
    public String getName() {
        return profile != null ? (String) profile.get("nickname") : null;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
