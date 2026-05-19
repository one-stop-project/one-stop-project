package com.sparta.one_stop.domain.product.dto.response;

import com.sparta.one_stop.domain.product.entity.ProductItem;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class ProductItemResponse {

    private Long itemId;
    private String optionName;
    private Long price;
    private Long stock;

    public static ProductItemResponse from(ProductItem item) {
        return ProductItemResponse.builder()
            .itemId(item.getId())
            .optionName(buildOptionName(item))
            .price(item.getPrice())
            .stock(item.getStock())
            .build();
    }

    // 옵션값을 " / " 로 결합
    // 빈 값은 제외
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
