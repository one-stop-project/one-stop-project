package com.sparta.one_stop.domain.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

// 상품 등록 요청 DTO
// 등록 제약: 카테고리 1~3개 / 이미지 1~10장 / 옵션 1~5개
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCreateRequest {

    @NotBlank(message = "상품명은 필수입니다")
    @Size(max = 200, message = "상품명은 200자 이하여야 합니다")
    private String name;

    @NotBlank(message = "상품 설명은 필수입니다")
    private String description;

    @NotEmpty(message = "카테고리는 최소 1개 이상 선택해야 합니다")
    @Size(min = 1, max = 3, message = "카테고리는 1~3개까지 선택 가능합니다")
    private List<Long> categoryIds;

    @NotEmpty(message = "상품 이미지는 최소 1장 이상 필요합니다")
    @Size(min = 1, max = 10, message = "상품 이미지는 1~10장까지 등록 가능합니다")
    private List<@NotBlank(message = "이미지 URL은 비어있을 수 없습니다") String> imageUrls;

    @Size(max = 10, message = "태그는 최대 10개까지 가능합니다")
    private List<@NotBlank(message = "태그는 비어있을 수 없습니다") @Size(max = 30, message = "태그는 30자 이하여야 합니다") String> tags;

    @Size(max = 5, message = "옵션명은 최대 5개까지 가능합니다")
    private List<String> optionNames;

    @NotEmpty(message = "상품 옵션은 최소 1개 이상 필요합니다")
    @Size(min = 1, max = 5, message = "상품 옵션은 1~5개까지 등록 가능합니다")
    @Valid
    private List<ProductItemCreateRequest> items;

    // 옵션명 슬롯별 추출 (0-based index)
    // 슬롯이 비어있으면 null 반환
    public String getOptionName(int index) {
        if (optionNames == null || index >= optionNames.size()) {
            return null;
        }
        return optionNames.get(index);
    }

    // 썸네일 URL
    public String getThumbnailUrl() {
        return imageUrls.get(0);
    }
}
