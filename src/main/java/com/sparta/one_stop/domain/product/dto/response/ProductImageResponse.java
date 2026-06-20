package com.sparta.one_stop.domain.product.dto.response;

import com.sparta.one_stop.domain.product.entity.ProductImage;
import lombok.Builder;
import lombok.Getter;

// 판매자/관리자용 개별 이미지 응답 — 이미지 식별자(imageId)를 노출해
// 이미지 개별 삭제(DELETE .../images/{imageId})·대표지정(PATCH .../images/{imageId}/thumbnail) 호출을 가능하게 한다.
@Getter
@Builder
public class ProductImageResponse {

    private Long imageId;
    private String imageUrl;
    private int displayOrder;
    private boolean thumbnail;

    public static ProductImageResponse from(ProductImage image) {
        return ProductImageResponse.builder()
            .imageId(image.getId())
            .imageUrl(image.getImageUrl())
            .displayOrder(image.getDisplayOrder())
            .thumbnail(image.isThumbnail())
            .build();
    }
}
