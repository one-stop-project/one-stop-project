package com.sparta.one_stop.domain.product.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 상품 옵션 등록 요청 DTO
// 옵션값은 1~5개 (사용하지 않는 슬롯은 null 허용)
// 옵션값 조합은 동일 상품 내 중복 불가 (Service에서 검증)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductItemCreateRequest {

    private String optionValue1;
    private String optionValue2;
    private String optionValue3;
    private String optionValue4;
    private String optionValue5;

    @NotNull(message = "가격은 필수입니다")
    @Min(value = 100, message = "판매 가격은 100원 이상이어야 합니다")
    private Long price;

    @NotNull(message = "재고는 필수입니다")
    @PositiveOrZero(message = "재고는 0 이상이어야 합니다")
    private Long stock;

    // 옵션값 조합을 중복 검증용 키로 변환
    // null → 빈 문자열로 정규화
    public String getOptionCombinationKey() {
        return String.join("||",
            nullToEmpty(optionValue1),
            nullToEmpty(optionValue2),
            nullToEmpty(optionValue3),
            nullToEmpty(optionValue4),
            nullToEmpty(optionValue5)
        );
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
