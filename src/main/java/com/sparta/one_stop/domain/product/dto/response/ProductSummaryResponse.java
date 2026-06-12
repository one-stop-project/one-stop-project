package com.sparta.one_stop.domain.product.dto.response;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

// @Jacksonized: Redis 캐시(GenericJackson2JsonRedisSerializer) 역직렬화를 위해 빌더 기반 생성자를 노출한다.
// (no-arg 생성자가 없으면 캐시 read 시 "no Creators" 로 역직렬화 실패 → 캐시가 항상 미스로 폴백된다)
@Getter
@Builder
@Jacksonized
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
