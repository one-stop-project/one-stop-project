package com.sparta.one_stop.domain.product.dto.response;

import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.global.enums.product.ProductItemStatus;
import lombok.Builder;
import lombok.Getter;

// 판매자용 옵션 응답 — 재고 수량(stock)과 옵션 상태(STOP 포함)를 그대로 노출한다.
// 구매자용(BuyerProductItemResponse)은 stock을 숨기고 ON_SALE 옵션만 보여주지만,
// 판매자는 재고 관리·판매중단(STOP) 옵션 관리를 위해 전부 필요하다.
@Getter
@Builder
public class SellerProductItemResponse {

    private Long itemId;
    private String optionName;
    private Long price;
    private Long stock;
    private ProductItemStatus status;

    public static SellerProductItemResponse from(ProductItem item) {
        return SellerProductItemResponse.builder()
            .itemId(item.getId())
            .optionName(item.getOptionSummary())
            .price(item.getPrice())
            .stock(item.getStock())
            .status(item.getStatus())
            .build();
    }
}
