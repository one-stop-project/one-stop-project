package com.sparta.one_stop.domain.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 카테고리 이름 수정 요청
public record CategoryUpdateRequest(

        @NotBlank(message = "카테고리명은 필수입니다")
        @Size(max = 50, message = "카테고리명은 50자 이하여야 합니다")
        String name
) {
}
