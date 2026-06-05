package com.sparta.one_stop.dummy.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// 네이버 검색 응답 래퍼 — 상품 목록(items) 감쌈
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverShopResponse(
        int total,
        int start,
        int display,
        List<NaverShopItem> items
) {
}
