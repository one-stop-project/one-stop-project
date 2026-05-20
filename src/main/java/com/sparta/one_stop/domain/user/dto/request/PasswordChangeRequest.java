package com.sparta.one_stop.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 비밀번호 변경 요청 DTO
 *
 * API: PATCH /api/users/me/password
 *
 * 정책:
 * - 새 비밀번호: 8자 이상, 영문 + 숫자 + 특수문자 필수
 */
public record PasswordChangeRequest(
    @NotBlank(message = "현재 비밀번호는 필수입니다")
    String currentPassword,

    @NotBlank(message = "새 비밀번호는 필수입니다")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
        message = "비밀번호는 8자 이상, 영문+숫자+특수문자를 포함해야 합니다"
    )
    String newPassword
) {
}
