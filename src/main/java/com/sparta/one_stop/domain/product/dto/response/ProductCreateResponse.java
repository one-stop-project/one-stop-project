package com.sparta.one_stop.domain.product.dto.response;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProductCreateResponse {

    private Long productId;
    private String name;
    private ProductStatus status;
    private List<ProductItemResponse> items;

    public static ProductCreateResponse from(Product product) {
        List<ProductItemResponse> items = product.getProductItems().stream()
            .map(ProductItemResponse::from)
            .toList();

        return ProductCreateResponse.builder()
            .productId(product.getId())
            .name(product.getName())
            .status(product.getStatus())
            .items(items)
            .build();
    }
}
