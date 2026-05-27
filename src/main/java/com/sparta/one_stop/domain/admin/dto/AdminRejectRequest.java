package com.sparta.one_stop.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminRejectRequest(
    // 반려/강제비활성화 사유 필수
    @NotBlank(message = "사유는 필수입니다")
    String reason
) {}
