package com.sparta.one_stop.domain.product.repository;

// 인기 상품 — 판매수 3일 윈도우 시간 가중 집계 row
// recent: 0~1일, middle: 1~2일, oldest: 2~3일 (단위: SUM(quantity))
public interface SalesWeightedRow {
    Long getProductId();
    Long getRecent();
    Long getMiddle();
    Long getOldest();
}
