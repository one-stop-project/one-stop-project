package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.product.SortType;
import com.sparta.one_stop.global.enums.user.SellerStatus;

import java.util.List;

// 구매자 상품 검색/목록 조건 — QueryDSL 동적 쿼리 입력
// keyword는 서비스에서 FULLTEXT 새니타이즈 후 전달(null이면 키워드 필터 미적용)
// tags는 OR 조건 (지정한 태그 중 하나라도 가진 상품 매칭), null이면 필터 미적용
public record ProductSearchCond(
    ProductStatus productStatus,
    SellerStatus sellerStatus,
    String keyword,
    Long categoryId,
    Long minPrice,
    Long maxPrice,
    SortType sort,
    List<String> tags
) {
}
