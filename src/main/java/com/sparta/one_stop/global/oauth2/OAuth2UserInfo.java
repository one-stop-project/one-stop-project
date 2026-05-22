package com.sparta.one_stop.global.oauth2;

import java.util.Map;

public interface OAuth2UserInfo {
    String getProviderId();  // 소셜 서비스의 고유 식별자 (예: 카카오의 회원번호)
    String getProvider();    // 제공자 이름 (예: kakao, google)
    String getEmail();       // 이메일
    String getName();        // 이름 또는 닉네임
    Map<String, Object> getAttributes(); // 원본 데이터
}
