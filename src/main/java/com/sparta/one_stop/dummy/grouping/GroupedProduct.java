package com.sparta.one_stop.dummy.grouping;

import java.util.List;

// 변형 그룹핑 결과 — 원본 listing들을 "하나의 기본 상품 + 옵션 변형"으로 묶은 것.
// 정체성(baseSourceKey/변형의 listingSourceKey)은 원본 raw 기반, 표현(name/description)은 LLM 생성.
public record GroupedProduct(
        String baseSourceKey,
        String name,
        String description,
        List<Long> categoryIds,          // 우리 카테고리 매핑 (1~3개)
        List<String> optionAxisNames,    // 0~5개 (예: ["용량","색상"]). 빈 리스트 = 무옵션
        String thumbnailImageUrl,        // 사전 저장(다운로드 완료)된 우리 측 이미지 URL
        List<ProductVariant> variants
) {
}
