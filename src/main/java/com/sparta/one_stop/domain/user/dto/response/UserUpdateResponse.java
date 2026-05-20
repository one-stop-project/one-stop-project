package com.sparta.one_stop.domain.user.dto.response;

import com.sparta.one_stop.domain.user.entity.User;

/**
 * 회원 정보 수정 응답 DTO
 *
 * API: PATCH /api/users/me
 */
public record UserUpdateResponse(
    Long userId,
    String name,
    String phone,
    String address
) {

    public static UserUpdateResponse from(User user) {
        return new UserUpdateResponse(
            user.getId(),
            user.getName(),
            user.getPhone(),
            user.getAddress()
        );
    }
}
