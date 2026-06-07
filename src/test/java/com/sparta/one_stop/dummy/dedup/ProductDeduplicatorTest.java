package com.sparta.one_stop.dummy.dedup;

import com.sparta.one_stop.dummy.naver.dto.NaverShopItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductDeduplicator")
class ProductDeduplicatorTest {

    private final ProductDeduplicator deduplicator = new ProductDeduplicator();

    @Test
    @DisplayName("대표 카탈로그(productType=1)만 통과, 2·3은 제외")
    void keepsOnlyCatalogRepresentative() {
        List<NaverShopItem> result = deduplicator.dedup(List.of(
            item("갤럭시 S24", "삼성전자", "삼성전자", "1"),
            item("아이폰 17", "Apple", "Apple", "2"),
            item("갤럭시 워치", "삼성전자", "삼성전자", "3")
        ));

        assertThat(result).extracting(NaverShopItem::title).containsExactly("갤럭시 S24");
    }

    @Test
    @DisplayName("brand+maker+정규화 title 같으면 중복 제거 (공백·대소문자 무시)")
    void removesDuplicatesByNormalizedKey() {
        List<NaverShopItem> result = deduplicator.dedup(List.of(
            item("Galaxy S24", "삼성전자", "삼성전자", "1"),
            item("galaxy  s24", "삼성전자", "삼성전자", "1"),   // 공백·대소문자만 다름 → 중복
            item("아이폰 17", "Apple", "Apple", "1")
        ));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(NaverShopItem::title)
            .containsExactly("Galaxy S24", "아이폰 17");
    }

    @Test
    @DisplayName("브랜드 다르면 같은 title이어도 별개")
    void keepsDistinctWhenBrandDiffers() {
        List<NaverShopItem> result = deduplicator.dedup(List.of(
            item("케이스", "삼성", "삼성", "1"),
            item("케이스", "슈피겐", "슈피겐", "1")
        ));

        assertThat(result).hasSize(2);
    }

    private NaverShopItem item(String title, String brand, String maker, String productType) {
        return new NaverShopItem(title, "link", "image", "1000", "0", brand, maker,
            "디지털", "휴대폰", "자급제", "안드로이드", "pid-" + title, productType, "네이버");
    }
}
