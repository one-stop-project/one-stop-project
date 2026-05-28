package com.sparta.one_stop.domain.product.dto.response;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PopularProductResponse {

    private int rank;
    private Long productId;
    private String name;
    private String thumbnailUrl;
    private Long minPrice;
    private long salesCount;

    public static PopularProductResponse from(int rank, Product product) {
        long minPrice = product.getProductItems().stream()
            .filter(ProductItem::isOnSale)
            .mapToLong(ProductItem::getPrice)
            .min()
            .orElse(0L);

        return PopularProductResponse.builder()
            .rank(rank)
            .productId(product.getId())
            .name(product.getName())
            .thumbnailUrl(product.getThumbnailUrl())
            .minPrice(minPrice)
            .salesCount(product.getSalesCount())
            .build();
    }
}
