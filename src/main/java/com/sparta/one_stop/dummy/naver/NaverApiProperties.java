package com.sparta.one_stop.dummy.naver;

import org.springframework.boot.context.properties.ConfigurationProperties;

// naver.api.* 설정값 바인딩 (Client ID/Secret, baseUrl)
@ConfigurationProperties(prefix = "naver.api")
public class NaverApiProperties {

    private String clientId;
    private String clientSecret;
    private String baseUrl;

    public NaverApiProperties() {
    }

    public NaverApiProperties(String clientId, String clientSecret, String baseUrl) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.baseUrl = baseUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
