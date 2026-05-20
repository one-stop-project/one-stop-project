package com.sparta.one_stop.domain.product.dto.response;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductDeleteResponse {

    private Long productId;
    private ProductStatus status;

    public static ProductDeleteResponse from(Product product) {
        return ProductDeleteResponse.builder()
            .productId(product.getId())
            .status(product.getStatus())
            .build();
    }
}
