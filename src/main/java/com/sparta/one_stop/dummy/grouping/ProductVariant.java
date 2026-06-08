package com.sparta.one_stop.dummy.grouping;

import java.util.List;

// 개별 변형 — 원본 listing 1건에 대응. optionValues는 GroupedProduct.optionAxisNames와 같은 순서.
// 재고는 원본에 없어 writer가 더미 기본값으로 채운다.
public record ProductVariant(
        String listingSourceKey,
        List<String> optionValues,
        Long sourcePrice
) {
}
