package com.sparta.one_stop.domain.product.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductImageAddResponse {

    private int addedImageCount;
    private int totalImageCount;
    private String thumbnailUrl;
}
