package com.sparta.one_stop.domain.seller.dto.response;

import com.sparta.one_stop.domain.admin.entity.AdminActionHistory;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.global.enums.product.ProductStatus;

import java.time.LocalDateTime;

public record SellerProductRejectReasonResponse(
    Long productId,
    String productName,
    ProductStatus productStatus,
    String reason,
    LocalDateTime rejectedAt
) {
    public static SellerProductRejectReasonResponse of(Product product, AdminActionHistory history) {
        return new SellerProductRejectReasonResponse(
            product.getId(), product.getName(), product.getStatus(),
            history == null ? null : history.getReason(),
            history == null ? null : history.getCreatedAt()
        );
    }
}
