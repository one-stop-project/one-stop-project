package com.sparta.one_stop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductViewCountSyncService - Redis 누적 카운트 DB 동기화")
class ProductViewCountSyncServiceTest {

    private static final Long PRODUCT_ID = 10L;

    @Mock
    private ProductViewCountService viewCountService;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductViewCountSyncService syncService;

    @Test
    @DisplayName("누적 카운트가 0이면 DB 조회/업데이트 없이 즉시 종료한다")
    void syncOne_zeroCount_skipsDbAccess() {
        // given
        given(viewCountService.flushAndGetCount(PRODUCT_ID)).willReturn(0L);

        // when
        syncService.syncOne(PRODUCT_ID);

        // then
        then(productRepository).should(never()).findById(PRODUCT_ID);
    }

    @Test
    @DisplayName("누적 카운트가 양수이면 Product.syncViewCount로 누적값이 반영된다")
    void syncOne_positiveCount_appliesToProduct() {
        // given
        Product product = createProduct(0L);
        given(viewCountService.flushAndGetCount(PRODUCT_ID)).willReturn(15L);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

        // when
        syncService.syncOne(PRODUCT_ID);

        // then
        assertThat(product.getViewCount()).isEqualTo(15L);
    }

    @Test
    @DisplayName("기존 viewCount가 있는 상품도 누적값이 가산되어 반영된다")
    void syncOne_existingViewCount_accumulates() {
        // given
        Product product = createProduct(100L);
        given(viewCountService.flushAndGetCount(PRODUCT_ID)).willReturn(7L);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

        // when
        syncService.syncOne(PRODUCT_ID);

        // then
        assertThat(product.getViewCount()).isEqualTo(107L);
    }

    @Test
    @DisplayName("productId에 해당하는 상품이 없으면 카운트 반영 없이 종료한다")
    void syncOne_productNotFound_noUpdate() {
        // given
        given(viewCountService.flushAndGetCount(PRODUCT_ID)).willReturn(5L);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

        // when & then — 예외 없이 정상 종료
        syncService.syncOne(PRODUCT_ID);
    }

    private Product createProduct(long initialViewCount) {
        Seller seller = Seller.builder()
            .shopName("shop").businessNumber("1234567890").build();
        Product product = Product.builder()
            .seller(seller).name("test").thumbnailUrl("url").build();
        product.approve();
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        ReflectionTestUtils.setField(product, "viewCount", initialViewCount);
        return product;
    }
}
