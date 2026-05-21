package com.sparta.one_stop.domain.product.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductImageDeleteResponse {

    private Long deletedImageId;
    private int remainingImageCount;
    private String thumbnailUrl;
}
