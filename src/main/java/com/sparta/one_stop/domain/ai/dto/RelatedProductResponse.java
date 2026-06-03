package com.sparta.one_stop.domain.ai.dto;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;

public record RelatedProductResponse(
    Long productId,
    String name,
    String thumbnailUrl,
    Long minPrice,
    long salesCount
) {
    public static RelatedProductResponse from(Product product) {
        long minPrice = product.getProductItems().stream()
            .filter(ProductItem::isOnSale)
            .mapToLong(ProductItem::getPrice)
            .min()
            .orElse(0L);

        return new RelatedProductResponse(
            product.getId(),
            product.getName(),
            product.getThumbnailUrl(),
            minPrice,
            product.getSalesCount()
        );
    }
}
