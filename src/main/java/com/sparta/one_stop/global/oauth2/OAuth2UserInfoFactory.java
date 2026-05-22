package com.sparta.one_stop.global.oauth2;

import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;

import java.util.Map;

public class OAuth2UserInfoFactory {

    public static OAuth2UserInfo getOAuth2UserInfo(String provider, Map<String, Object> attributes) {
        return switch (provider.toLowerCase()) {
            case "kakao" -> new KakaoOAuth2UserInfo(attributes);
            // case "google" -> new GoogleOAuth2UserInfo(attributes);
            // case "naver" -> new NaverOAuth2UserInfo(attributes);
            default -> throw new CustomException(ErrorCode.AUTH_017, "지원하지 않는 OAuth2 제공자입니다: " + provider);
        };
    }
}
