package com.sparta.one_stop.domain.product.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductImageThumbnailResponse {

    private Long thumbnailImageId;
    private String thumbnailUrl;
}
