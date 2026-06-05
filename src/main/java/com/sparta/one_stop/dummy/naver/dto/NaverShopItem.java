package com.sparta.one_stop.dummy.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 네이버 쇼핑 검색 결과의 상품 1건
// lprice/hprice/productType은 네이버가 문자열로 줘서 String으로 받음 (모르는 필드 무시)
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverShopItem(
        String title,
        String link,
        String image,
        String lprice,
        String hprice,
        String brand,
        String maker,
        String category1,
        String category2,
        String category3,
        String category4,
        String productId,
        String productType,
        String mallName
) {
    public NaverShopItem withTitle(String title) {
        return new NaverShopItem(
                title,
                link,
                image,
                lprice,
                hprice,
                brand,
                maker,
                category1,
                category2,
                category3,
                category4,
                productId,
                productType,
                mallName
        );
    }
}
