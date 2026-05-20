package com.sparta.one_stop.domain.auth.dto.result;

import com.sparta.one_stop.domain.auth.dto.response.TokenRefreshResponse;

public record RefreshResult(
        TokenRefreshResponse response,  // 클라이언트에 응답할 DTO (AT만 포함)
        String newRefreshToken          // 쿠키에 넣을 새 RT (응답 본문엔 안 들어감)
) {}
