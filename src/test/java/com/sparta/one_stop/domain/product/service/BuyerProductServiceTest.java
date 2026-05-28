package com.sparta.one_stop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.sparta.one_stop.domain.product.dto.response.ProductDetailResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductSummaryResponse;
import com.sparta.one_stop.domain.product.entity.Category;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductCategoryMapping;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuyerProductService - 구매자 상품 조회")
class BuyerProductServiceTest {

    private static final Long PRODUCT_ID = 10L;
    private static final Long USER_ID = 1L;
    private static final Long SELLER_ID = 100L;
    private static final Long CATEGORY_ID_1 = 201L;
    private static final Long CATEGORY_ID_2 = 202L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductViewCountService viewCountService;

    @InjectMocks
    private BuyerProductService buyerProductService;

    private final Pageable pageable = PageRequest.of(0, 20);

    // ===== 객체 생성 헬퍼 =====

    private Seller seller(SellerStatus status) {
        Seller seller = Seller.builder()
            .shopName("테스트샵").businessNumber("1234567890").build();
        if (status == SellerStatus.APPROVED) {
            seller.approve();
        }
        ReflectionTestUtils.setField(seller, "id", SELLER_ID);
        return seller;
    }

    private Product product(Seller seller, ProductStatus status) {
        Product product = Product.builder()
            .seller(seller).name("테스트상품").thumbnailUrl("url").build();
        switch (status) {
            case APPROVED -> product.approve();
            case REJECTED -> product.reject();
            case DISCONTINUED -> product.discontinue();
            case FORCE_INACTIVE -> product.forceInactive();
            case APPROVE_REQUESTED -> { /* 생성 기본값 */ }
        }
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        return product;
    }

    private Product approvedProduct() {
        return product(seller(SellerStatus.APPROVED), ProductStatus.APPROVED);
    }

    private void attachCategory(Product product, Long categoryId) {
        Category category = Category.builder().name("카테고리").build();
        ReflectionTestUtils.setField(category, "id", categoryId);
        ProductCategoryMapping mapping = ProductCategoryMapping.builder()
            .product(product).category(category).build();
        product.getCategoryMappings().add(mapping);
    }

    // ===== search =====

    @Nested
    @DisplayName("search - 검색/목록")
    class Search {

        @Test
        @DisplayName("keyword가 null이면 repository에 null 키워드로 전달된다")
        void nullKeyword_passesNullToRepository() {
            // given
            given(productRepository.searchApproved(any(), any(), any(), any(), any()))
                .willReturn(Page.empty(pageable));

            // when
            buyerProductService.search(null, null, pageable);

            // then
            then(productRepository).should().searchApproved(
                eq(ProductStatus.APPROVED), eq(SellerStatus.APPROVED),
                isNull(), isNull(), eq(pageable));
        }

        @Test
        @DisplayName("keyword가 빈 문자열이면 repository에 null로 정규화되어 전달된다")
        void blankKeyword_normalizedToNull() {
            // given
            given(productRepository.searchApproved(any(), any(), any(), any(), any()))
                .willReturn(Page.empty(pageable));

            // when
            buyerProductService.search("", null, pageable);

            // then
            then(productRepository).should().searchApproved(
                any(), any(), isNull(), any(), any());
        }

        @Test
        @DisplayName("keyword가 공백만 있으면 repository에 null로 정규화되어 전달된다")
        void whitespaceKeyword_normalizedToNull() {
            // given
            given(productRepository.searchApproved(any(), any(), any(), any(), any()))
                .willReturn(Page.empty(pageable));

            // when
            buyerProductService.search("   ", null, pageable);

            // then
            then(productRepository).should().searchApproved(
                any(), any(), isNull(), any(), any());
        }

        @Test
        @DisplayName("정상 keyword는 repository에 그대로 전달된다")
        void validKeyword_passedAsIs() {
            // given
            given(productRepository.searchApproved(any(), any(), any(), any(), any()))
                .willReturn(Page.empty(pageable));

            // when
            buyerProductService.search("맥북", 5L, pageable);

            // then
            then(productRepository).should().searchApproved(
                eq(ProductStatus.APPROVED), eq(SellerStatus.APPROVED),
                eq("맥북"), eq(5L), eq(pageable));
        }
    }

    // ===== getDetail =====

    @Nested
    @DisplayName("getDetail - 단건 상세")
    class GetDetail {

        @Test
        @DisplayName("상품이 없으면 PRODUCT_001 예외가 발생하고 recordView는 호출되지 않는다")
        void productNotFound_throwsAndNoRecordView() {
            // given
            given(productRepository.findWithCollectionsById(PRODUCT_ID))
                .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> buyerProductService.getDetail(PRODUCT_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_001);

            then(viewCountService).should(never()).recordView(any(), any());
        }

        @Test
        @DisplayName("상품 상태가 APPROVED가 아니면 PRODUCT_002 예외가 발생한다")
        void productNotApproved_throwsProduct002() {
            // given
            Product rejected = product(seller(SellerStatus.APPROVED), ProductStatus.REJECTED);
            given(productRepository.findWithCollectionsById(PRODUCT_ID))
                .willReturn(Optional.of(rejected));

            // when & then
            assertThatThrownBy(() -> buyerProductService.getDetail(PRODUCT_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_002);

            then(viewCountService).should(never()).recordView(any(), any());
        }

        @Test
        @DisplayName("판매자 상태가 APPROVED가 아니면 PRODUCT_002 예외가 발생한다")
        void sellerNotApproved_throwsProduct002() {
            // given - 상품은 APPROVED이지만 판매자 status는 PENDING
            Product productWithPendingSeller =
                product(seller(SellerStatus.PENDING), ProductStatus.APPROVED);
            given(productRepository.findWithCollectionsById(PRODUCT_ID))
                .willReturn(Optional.of(productWithPendingSeller));

            // when & then
            assertThatThrownBy(() -> buyerProductService.getDetail(PRODUCT_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_002);

            then(viewCountService).should(never()).recordView(any(), any());
        }

        @Test
        @DisplayName("정상 조회 시 recordView가 (productId, userId)로 호출되고 응답이 반환된다")
        void approved_callsRecordViewAndReturnsResponse() {
            // given
            Product product = approvedProduct();
            given(productRepository.findWithCollectionsById(PRODUCT_ID))
                .willReturn(Optional.of(product));

            // when
            ProductDetailResponse response =
                buyerProductService.getDetail(PRODUCT_ID, USER_ID);

            // then
            assertThat(response).isNotNull();
            then(viewCountService).should(times(1)).recordView(PRODUCT_ID, USER_ID);
        }

        @Test
        @DisplayName("비로그인(userId=null) 조회 시에도 recordView가 호출된다")
        void guestUser_stillCallsRecordView() {
            // given
            Product product = approvedProduct();
            given(productRepository.findWithCollectionsById(PRODUCT_ID))
                .willReturn(Optional.of(product));

            // when
            buyerProductService.getDetail(PRODUCT_ID, null);

            // then
            then(viewCountService).should(times(1)).recordView(PRODUCT_ID, null);
        }
    }

    // ===== getRelated =====

    @Nested
    @DisplayName("getRelated - 연관 상품")
    class GetRelated {

        @Test
        @DisplayName("상품이 없으면 PRODUCT_001 예외가 발생한다")
        void productNotFound_throws() {
            // given
            given(productRepository.findWithCollectionsById(PRODUCT_ID))
                .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> buyerProductService.getRelated(PRODUCT_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_001);
        }

        @Test
        @DisplayName("카테고리 매핑이 비어있으면 빈 리스트를 반환하고 findRelated는 호출되지 않는다")
        void emptyCategoryMappings_returnsEmptyAndSkipsRepo() {
            // given - approvedProduct는 categoryMappings 비어있음
            Product product = approvedProduct();
            given(productRepository.findWithCollectionsById(PRODUCT_ID))
                .willReturn(Optional.of(product));

            // when
            List<ProductSummaryResponse> result = buyerProductService.getRelated(PRODUCT_ID);

            // then
            assertThat(result).isEmpty();
            then(productRepository).should(never())
                .findRelated(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("카테고리 매핑이 있으면 findRelated에 categoryIds와 excludeId가 전달된다")
        void hasCategoryMappings_callsFindRelatedWithCorrectArgs() {
            // given
            Product product = approvedProduct();
            attachCategory(product, CATEGORY_ID_1);
            attachCategory(product, CATEGORY_ID_2);
            given(productRepository.findWithCollectionsById(PRODUCT_ID))
                .willReturn(Optional.of(product));
            given(productRepository.findRelated(any(), any(), any(), any(), any()))
                .willReturn(List.of());

            // when
            buyerProductService.getRelated(PRODUCT_ID);

            // then
            then(productRepository).should().findRelated(
                argThat(ids -> ids.containsAll(List.of(CATEGORY_ID_1, CATEGORY_ID_2))
                    && ids.size() == 2),
                eq(PRODUCT_ID),
                eq(ProductStatus.APPROVED),
                eq(SellerStatus.APPROVED),
                any(Pageable.class)
            );
        }
    }

    // ===== getPopular =====

    @Nested
    @DisplayName("getPopular - 인기 상품")
    class GetPopular {

        @Test
        @DisplayName("findApproved를 APPROVED 상태 + 전달된 pageable로 호출한다")
        void delegatesToFindApproved() {
            // given
            given(productRepository.findApproved(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

            // when
            buyerProductService.getPopular(pageable);

            // then
            then(productRepository).should().findApproved(
                eq(ProductStatus.APPROVED), eq(SellerStatus.APPROVED), eq(pageable));
        }
    }
}
