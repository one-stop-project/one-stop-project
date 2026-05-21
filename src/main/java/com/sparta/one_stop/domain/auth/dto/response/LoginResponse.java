package com.sparta.one_stop.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sparta.one_stop.domain.user.entity.User;

public record LoginResponse(
    String accessToken,
    @JsonIgnore String refreshToken,
    long expiresIn,
    Long userId,
    String email,
    String name,
    String role
) {
    // of 메서드 파라미터에서도 refreshToken 제거
    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn, User user) {
        return new LoginResponse(accessToken, refreshToken, expiresIn, user.getId(), user.getEmail(), user.getName(), user.getRole().name());
    }
}
