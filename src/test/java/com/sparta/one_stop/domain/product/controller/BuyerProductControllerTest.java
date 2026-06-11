package com.sparta.one_stop.domain.product.controller;

import com.sparta.one_stop.global.enums.product.SortType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BuyerProductController.parseSortType — 정렬 파라미터 안전 변환")
class BuyerProductControllerTest {

    @Test
    @DisplayName("정상 정렬값은 해당 SortType으로 변환")
    void validSortType() {
        assertThat(BuyerProductController.parseSortType("PRICE_ASC")).isEqualTo(SortType.PRICE_ASC);
        assertThat(BuyerProductController.parseSortType("POPULAR")).isEqualTo(SortType.POPULAR);
    }

    @Test
    @DisplayName("소문자/혼합 대소문자도 변환된다")
    void caseInsensitive() {
        assertThat(BuyerProductController.parseSortType("price_desc")).isEqualTo(SortType.PRICE_DESC);
        assertThat(BuyerProductController.parseSortType("Latest")).isEqualTo(SortType.LATEST);
    }

    @Test
    @DisplayName("null·공백·잘못된 값은 기본 정렬(LATEST)로 폴백")
    void fallbackToLatest() {
        assertThat(BuyerProductController.parseSortType(null)).isEqualTo(SortType.LATEST);
        assertThat(BuyerProductController.parseSortType("   ")).isEqualTo(SortType.LATEST);
        assertThat(BuyerProductController.parseSortType("BOGUS")).isEqualTo(SortType.LATEST);
    }
}
