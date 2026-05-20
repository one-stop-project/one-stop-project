package com.sparta.one_stop.domain.product.dto.request;

import com.sparta.one_stop.global.enums.product.ProductItemStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

// 옵션 수정 요청 (모든 필드 nullable - 전달된 필드만 수정)
public record ItemUpdateRequest(

        @Min(value = 100, message = "판매 가격은 100원 이상이어야 합니다")
        Long price,

        // 재고 절대값 (덮어쓰기)
        @PositiveOrZero(message = "재고는 0 이상이어야 합니다")
        Long stock,

        ProductItemStatus status
) {
}
