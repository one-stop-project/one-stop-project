package com.sparta.one_stop.domain.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 카테고리 생성 요청 (parentId null이면 루트)
public record CategoryCreateRequest(

        @NotBlank(message = "카테고리명은 필수입니다")
        @Size(max = 50, message = "카테고리명은 50자 이하여야 합니다")
        String name,

        Long parentId
) {
}
