package com.sparta.one_stop.domain.auth.dto.response;

import lombok.Builder;

@Builder
public record TokenRefreshResponse(
    String accessToken,
    long expiresIn
) {
    public static TokenRefreshResponse of(String accessToken, long expiresIn) {
        return new TokenRefreshResponse(accessToken, expiresIn);
    }
}
