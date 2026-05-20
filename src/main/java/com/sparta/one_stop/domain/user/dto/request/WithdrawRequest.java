package com.sparta.one_stop.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 회원 탈퇴 요청 DTO
 *
 * API: DELETE /api/users/me
 *
 * 정책: 탈퇴 시 비밀번호 재확인 필수 (실수/타인 조작 방지)
 */
public record WithdrawRequest(
    @NotBlank(message = "비밀번호는 필수입니다")
    String password
) {
}
