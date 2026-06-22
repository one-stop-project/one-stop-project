package com.sparta.one_stop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.sparta.one_stop.domain.product.dto.response.PopularProductResponse;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.product.ProductItemStatus;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.enums.user.UserRole;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PopularProductService - 인기 상품 ZSET 랭킹")
class PopularProductServiceTest {

    private static final Long PRODUCT_ID_1 = 10L;
    private static final Long PRODUCT_ID_2 = 20L;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    @SuppressWarnings("unchecked")
    private ZSetOperations<String, String> zSetOps;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private PopularProductService popularProductService;

    // ===== 객체 생성 헬퍼 =====

    private Product approvedProduct(Long id) {
        User user = User.builder()
            .email("seller" + id + "@test.com")
            .password("password")
            .name("seller")
            .role(UserRole.SELLER)
            .build();
        Seller seller = Seller.builder().user(user)
            .shopName("shop").businessNumber("1234567890").build();
        seller.approve();
        Product product = Product.builder().seller(seller).name("p" + id).thumbnailUrl("url").build();
        product.approve();
        ReflectionTestUtils.setField(product, "id", id);

        // ON_SALE 옵션 1개 추가 (isVisibleAndOnSale 통과용)
        ProductItem item = ProductItem.builder().price(1000L).stock(10L).build();
        ReflectionTestUtils.setField(item, "status", ProductItemStatus.ON_SALE);
        product.getProductItems().add(item);
        return product;
    }

    // ===== viewBucketKey =====

    @Nested
    @DisplayName("viewBucketKey - 시간 버킷 키 빌더")
    class ViewBucketKey {

        @Test
        @DisplayName("LocalDateTime을 yyyyMMddHH 형식의 prefix 키로 변환한다")
        void formatsToHourlyKey() {
            LocalDateTime t = LocalDateTime.of(2026, 5, 28, 14, 35);
            assertThat(PopularProductService.viewBucketKey(t))
                .isEqualTo("popular:view:bucket:1h:2026052814");
        }
    }

    // ===== getPopularProductIds =====

    @Nested
    @DisplayName("getPopularProductIds - search() POPULAR 분기에서 사용")
    class GetPopularProductIds {

        @Test
        @DisplayName("ZSET 결과를 Long ID 리스트로 변환해 반환한다")
        void returnsIdList() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOps);
            given(zSetOps.reverseRange(eq("popular:products"), eq(0L), anyLong()))
                .willReturn(Set.of("10", "20"));

            List<Long> result = popularProductService.getPopularProductIds(20);

            assertThat(result).containsExactlyInAnyOrder(10L, 20L);
        }

        @Test
        @DisplayName("ZSET이 비어있으면 빈 리스트를 반환한다")
        void emptyZSetReturnsEmpty() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOps);
            given(zSetOps.reverseRange(any(), anyLong(), anyLong()))
                .willReturn(Set.of());

            assertThat(popularProductService.getPopularProductIds(20)).isEmpty();
        }

        @Test
        @DisplayName("Redis 예외 시 빈 리스트를 반환한다 (조회 자체는 실패하지 않음)")
        void redisExceptionReturnsEmpty() {
            given(redisTemplate.opsForZSet()).willThrow(new RuntimeException("redis down"));

            assertThat(popularProductService.getPopularProductIds(20)).isEmpty();
        }
    }

    // ===== getPopular =====

    @Nested
    @DisplayName("getPopular - 인기 상품 TOP N 응답")
    class GetPopular {

        @Test
        @DisplayName("ZSET이 비어있으면 DB fallback (sales_count DESC) 경로를 탄다")
        void emptyZSetFallsBackToDb() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOps);
            given(zSetOps.reverseRangeWithScores(any(), anyLong(), anyLong()))
                .willReturn(Set.of());

            Product p = approvedProduct(PRODUCT_ID_1);
            Page<Product> fallbackPage = new PageImpl<>(List.of(p));
            given(productRepository.findApproved(any(), any(), any())).willReturn(fallbackPage);
            given(productRepository.findAllByIdsWithItems(List.of(PRODUCT_ID_1)))
                .willReturn(List.of(p));

            List<PopularProductResponse> result = popularProductService.getPopular(20);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo(PRODUCT_ID_1);
            assertThat(result.get(0).getRank()).isEqualTo(1);
        }

        @Test
        @DisplayName("Redis 예외 시 DB fallback 경로를 탄다 (조회 자체는 실패하지 않음)")
        void redisExceptionFallsBackToDb() {
            given(redisTemplate.opsForZSet()).willThrow(new RuntimeException("redis down"));

            Product p = approvedProduct(PRODUCT_ID_1);
            Page<Product> fallbackPage = new PageImpl<>(List.of(p));
            given(productRepository.findApproved(any(), any(), any())).willReturn(fallbackPage);
            given(productRepository.findAllByIdsWithItems(any())).willReturn(List.of(p));

            List<PopularProductResponse> result = popularProductService.getPopular(20);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("ZSET 순서대로 응답을 빌드한다 (rank 1, 2, ...)")
        void buildsResponseInZSetOrder() {
            Set<TypedTuple<String>> tuples = new java.util.LinkedHashSet<>();
            tuples.add(TypedTuple.of("20", 100.0)); // 더 높은 점수 → rank 1
            tuples.add(TypedTuple.of("10", 50.0));

            given(redisTemplate.opsForZSet()).willReturn(zSetOps);
            given(zSetOps.reverseRangeWithScores(any(), anyLong(), anyLong()))
                .willReturn(tuples);

            Product p1 = approvedProduct(PRODUCT_ID_1);
            Product p2 = approvedProduct(PRODUCT_ID_2);
            given(productRepository.findAllByIdsWithItems(any()))
                .willReturn(List.of(p1, p2));

            List<PopularProductResponse> result = popularProductService.getPopular(20);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getProductId()).isEqualTo(PRODUCT_ID_2);
            assertThat(result.get(0).getRank()).isEqualTo(1);
            assertThat(result.get(1).getProductId()).isEqualTo(PRODUCT_ID_1);
            assertThat(result.get(1).getRank()).isEqualTo(2);
        }
    }
}
