package com.sparta.one_stop.domain.product.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

// CacheConfig.redisCacheManager 가 쓰는 것과 동일한 serializer 로 캐시 값 라운드트립을 검증한다.
// 회귀 가드: @Jacksonized 누락 등으로 캐시 read(역직렬화)가 다시 깨지면 이 테스트가 실패한다.
@DisplayName("상품 응답 DTO - Redis 캐시 직렬화/역직렬화 라운드트립")
class ProductResponseCacheSerializationTest {

    private final GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();

    @Test
    @DisplayName("productList 캐시 값(CacheableProductList)이 content까지 역직렬화된다")
    void cacheableProductList_roundtrip() {
        CacheableProductList original = new CacheableProductList(
            List.of(ProductSummaryResponse.builder()
                .productId(1L).name("맥북").thumbnailUrl("/img.png")
                .minPrice(1_000_000L).salesCount(5L).viewCount(10L).build()),
            0, 10, 1L);

        Object back = serializer.deserialize(serializer.serialize(original));

        assertThat(back).isInstanceOf(CacheableProductList.class);
        CacheableProductList result = (CacheableProductList) back;
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).getName()).isEqualTo("맥북");
        assertThat(result.content().get(0).getMinPrice()).isEqualTo(1_000_000L);
        assertThat(result.content().get(0).getSalesCount()).isEqualTo(5L);
        assertThat(result.total()).isEqualTo(1L);
    }

    @Test
    @DisplayName("productDetail 캐시 값(BuyerProductDetailResponse)이 중첩 옵션까지 역직렬화된다")
    void buyerProductDetail_roundtrip() {
        BuyerProductDetailResponse original = BuyerProductDetailResponse.builder()
            .productId(1L).name("맥북").description("설명").thumbnailUrl("/img.png")
            .viewCount(10L).salesCount(5L).shopName("애플샵")
            .optionNames(List.of("색상"))
            .items(List.of(BuyerProductItemResponse.builder()
                .itemId(1L).optionName("블랙").price(1_000_000L).soldOut(false).build()))
            .imageUrls(List.of("/1.png"))
            .categoryNames(List.of("노트북"))
            .tags(List.of("애플"))
            .build();

        Object back = serializer.deserialize(serializer.serialize(original));

        assertThat(back).isInstanceOf(BuyerProductDetailResponse.class);
        BuyerProductDetailResponse result = (BuyerProductDetailResponse) back;
        assertThat(result.getName()).isEqualTo("맥북");
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getOptionName()).isEqualTo("블랙");
        assertThat(result.getItems().get(0).isSoldOut()).isFalse();
        assertThat(result.getTags()).containsExactly("애플");
    }
}
