package com.sparta.one_stop.domain.user.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원 정보 수정 요청 DTO
 *
 * API: PATCH /api/users/me
 *
 * 모든 필드 optional — null인 필드는 수정하지 않음 (User.updateProfile에서 분기)
 */
public record UserUpdateRequest(
    @Size(min = 2, max = 20, message = "이름은 2~20자여야 합니다")
    String name,

    @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "전화번호 형식: 010-XXXX-XXXX")
    String phone,

    String address
) {
}
