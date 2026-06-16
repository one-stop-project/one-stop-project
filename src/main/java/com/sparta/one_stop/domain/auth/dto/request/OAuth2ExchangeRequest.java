package com.sparta.one_stop.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 소셜 로그인 일회용 code 교환 요청 DTO
 *
 * API: POST /api/auth/oauth2/exchange
 * OAuth2SuccessHandler가 발급한 code를 Access Token으로 교환한다.
 */
public record OAuth2ExchangeRequest(
    @NotBlank(message = "인증 코드는 필수입니다")
    String code
) {
}
