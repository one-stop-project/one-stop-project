package com.sparta.one_stop.domain.product.dto.response;

import com.sparta.one_stop.domain.product.entity.ProductItem;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

// 구매자용 옵션 응답 — 재고 수량(stock) 대신 품절 여부(soldOut)만 노출한다.
// 재고 수량은 판매자 영업 정보라 구매자에게 보이지 않는다(판매자용 ProductItemResponse는 stock 유지).
// @Jacksonized: BuyerProductDetailResponse 안에 중첩되어 캐시 역직렬화되므로 빌더 기반 생성자가 필요하다.
@Getter
@Builder
@Jacksonized
public class BuyerProductItemResponse {

    private Long itemId;
    private String optionName;
    private Long price;
    private boolean soldOut;

    public static BuyerProductItemResponse from(ProductItem item) {
        return BuyerProductItemResponse.builder()
            .itemId(item.getId())
            .optionName(item.getOptionSummary())
            .price(item.getPrice())
            .soldOut(item.isSoldOut())
            .build();
    }
}
