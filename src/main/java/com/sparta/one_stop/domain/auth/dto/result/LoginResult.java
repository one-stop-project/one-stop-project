package com.sparta.one_stop.domain.auth.dto.result;

import com.sparta.one_stop.domain.auth.dto.response.LoginResponse;

public record LoginResult(
        LoginResponse response,  // 클라이언트 응답 (AT만)
        String refreshToken      // 쿠키용
) {}
