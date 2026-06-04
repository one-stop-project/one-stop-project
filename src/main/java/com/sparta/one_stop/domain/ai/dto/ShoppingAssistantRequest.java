package com.sparta.one_stop.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShoppingAssistantRequest(
    @NotBlank @Size(max = 500) String message,
    Long categoryId  // 현재 페이지 카테고리 힌트 (선택값, null이면 전체 검색)
) {}
