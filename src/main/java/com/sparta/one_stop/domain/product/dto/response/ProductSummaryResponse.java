package com.sparta.one_stop.domain.product.dto.response;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductSummaryResponse {

    private Long productId;
    private String name;
    private String thumbnailUrl;
    private Long minPrice;
    private long salesCount;
    private long viewCount;

    public static ProductSummaryResponse from(Product product) {
        long minPrice = product.getProductItems().stream()
            .filter(ProductItem::isOnSale)
            .mapToLong(ProductItem::getPrice)
            .min()
            .orElse(0L);

        return ProductSummaryResponse.builder()
            .productId(product.getId())
            .name(product.getName())
            .thumbnailUrl(product.getThumbnailUrl())
            .minPrice(minPrice)
            .salesCount(product.getSalesCount())
            .viewCount(product.getViewCount())
            .build();
    }
}
