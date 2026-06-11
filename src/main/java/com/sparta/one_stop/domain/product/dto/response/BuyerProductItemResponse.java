package com.sparta.one_stop.domain.product.dto.response;

import com.sparta.one_stop.domain.product.entity.ProductItem;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

// 구매자용 옵션 응답 — 재고 수량(stock) 대신 품절 여부(soldOut)만 노출한다.
// 재고 수량은 판매자 영업 정보라 구매자에게 보이지 않는다(판매자용 ProductItemResponse는 stock 유지).
@Getter
@Builder
public class BuyerProductItemResponse {

    private Long itemId;
    private String optionName;
    private Long price;
    private boolean soldOut;

    public static BuyerProductItemResponse from(ProductItem item) {
        return BuyerProductItemResponse.builder()
            .itemId(item.getId())
            .optionName(buildOptionName(item))
            .price(item.getPrice())
            .soldOut(item.isSoldOut())
            .build();
    }

    // 옵션값을 " / " 로 결합, 빈 값은 제외
    private static String buildOptionName(ProductItem item) {
        List<String> values = new ArrayList<>();
        addIfNotBlank(values, item.getOptionValue1());
        addIfNotBlank(values, item.getOptionValue2());
        addIfNotBlank(values, item.getOptionValue3());
        addIfNotBlank(values, item.getOptionValue4());
        addIfNotBlank(values, item.getOptionValue5());
        return String.join(" / ", values);
    }

    private static void addIfNotBlank(List<String> list, String value) {
        if (value != null && !value.isBlank()) {
            list.add(value);
        }
    }
}
