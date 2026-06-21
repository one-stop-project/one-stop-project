package com.sparta.one_stop.domain.seller.dto.response;

public record SellerProductSalesStatResponse(
    Long productId,
    String productName,
    String thumbnailUrl,
    long salesQuantity,
    long grossSalesAmount
) {
    public SellerProductSalesStatResponse(
        Long productId, String productName, String thumbnailUrl,
        Number salesQuantity, Number grossSalesAmount
    ) {
        this(
            productId, productName, thumbnailUrl,
            salesQuantity == null ? 0L : salesQuantity.longValue(),
            grossSalesAmount == null ? 0L : grossSalesAmount.longValue()
        );
    }
}
