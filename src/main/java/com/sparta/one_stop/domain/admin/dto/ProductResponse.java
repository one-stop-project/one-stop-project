package com.sparta.one_stop.domain.admin.dto;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.global.enums.product.ProductStatus;

// 상품 승인/반려 응답 DTO
public record ProductResponse(
    Long productId,
    Long sellerId,
    String name,
    String description,
    String thumbnailUrl,
    ProductStatus status
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getSeller().getId(),
            product.getName(),
            product.getDescription(),
            product.getThumbnailUrl(),
            product.getStatus()
        );
    }
}
