package com.sparta.one_stop.domain.product.dto.response;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class SellerProductListResponse {

    private Long productId;
    private String name;
    private ProductStatus status;
    private String thumbnailUrl;
    private Long minPrice;
    private long salesCount;
    private List<String> categoryNames;

    // Product -> DTO 변환 단건
    public static SellerProductListResponse from(Product product) {
        long minPrice = product.getProductItems().stream()
            .filter(ProductItem::isOnSale)
            .mapToLong(ProductItem::getPrice)
            .min()
            .orElse(0L);

        List<String> categoryNames = product.getCategoryMappings().stream()
            .map(m -> m.getCategory().getName())
            .toList();

        return SellerProductListResponse.builder()
            .productId(product.getId())
            .name(product.getName())
            .status(product.getStatus())
            .thumbnailUrl(product.getThumbnailUrl())
            .minPrice(minPrice)
            .salesCount(product.getSalesCount())
            .categoryNames(categoryNames)
            .build();
    }

    // Page<Product> -> Page<DTO> 변환
    public static Page<SellerProductListResponse> from(Page<Product> products) {
        return products.map(SellerProductListResponse::from);
    }
}
