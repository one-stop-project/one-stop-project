package com.sparta.one_stop.domain.product.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductUpdateRequest {

    @Size(max = 200, message = "상품명은 200자 이하여야 합니다")
    private String name;

    private String description;

    @Size(max = 500, message = "썸네일 URL은 500자 이하여야 합니다")
    private String thumbnailUrl;

    @Size(min = 1, max = 3, message = "카테고리는 1~3개까지 선택 가능합니다")
    private List<Long> categoryIds;
}
