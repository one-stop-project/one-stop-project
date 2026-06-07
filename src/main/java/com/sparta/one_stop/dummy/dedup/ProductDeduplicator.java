package com.sparta.one_stop.dummy.dedup;

import com.sparta.one_stop.dummy.naver.dto.NaverShopItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 네이버 검색 결과 배치 내 중복 제거 — 대표 카탈로그(productType=1)만 통과, brand+maker+정규화 title 키로 중복 컷
@Component
public class ProductDeduplicator {

    // 네이버 productType — "1" = 가격비교 카탈로그 대표(mallName "네이버"). 2/3은 개별 판매처라 제외
    private static final String CATALOG_REPRESENTATIVE = "1";

    public List<NaverShopItem> dedup(List<NaverShopItem> items) {
        Set<String> seen = new HashSet<>();
        List<NaverShopItem> result = new ArrayList<>();
        for (NaverShopItem item : items) {
            if (!CATALOG_REPRESENTATIVE.equals(item.productType())) {
                continue;
            }
            if (seen.add(dedupKey(item))) {
                result.add(item);
            }
        }
        return result;
    }

    private String dedupKey(NaverShopItem item) {
        return normalize(item.brand()) + "|" + normalize(item.maker()) + "|" + normalize(item.title());
    }

    // 공백 제거 + 소문자 통일 (표기 차이로 인한 중복 누락 방지)
    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "").toLowerCase();
    }
}
