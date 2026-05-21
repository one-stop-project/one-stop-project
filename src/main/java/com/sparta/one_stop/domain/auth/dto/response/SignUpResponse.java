package com.sparta.one_stop.domain.auth.dto.response;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.user.UserRole;
import lombok.Builder;

@Builder // 다른 곳에서 SignupResponse.builder()를 사용 중이라면 남겨두고, 아니라면 지워도 됩니다.
public record SignUpResponse(
    Long userId,
    String email,
    String name,
    UserRole role
) {
    public static SignUpResponse from(User user) {
        return new SignUpResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getRole()
        );
    }
}
