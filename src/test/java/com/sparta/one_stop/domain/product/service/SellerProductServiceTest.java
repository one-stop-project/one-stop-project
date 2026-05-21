package com.sparta.one_stop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sparta.one_stop.domain.product.dto.response.ProductImageDeleteResponse;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductImage;
import com.sparta.one_stop.domain.product.repository.CategoryRepository;
import com.sparta.one_stop.domain.product.repository.ProductImageRepository;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.global.enums.product.ProductImageStatus;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SellerProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SellerRepository sellerRepository;

    @InjectMocks
    private SellerProductService sellerProductService;

    private static final Long SELLER_USER_ID = 1L;
    private static final Long SELLER_ID = 100L;
    private static final Long OTHER_SELLER_ID = 200L;
    private static final Long PRODUCT_ID = 10L;

    // ===== 테스트 헬퍼 =====

    private Seller approvedSeller(Long sellerId) {
        User user = User.builder()
                .email("seller@test.com")
                .password("password")
                .name("판매자")
                .role(UserRole.SELLER)
                .build();
        ReflectionTestUtils.setField(user, "id", SELLER_USER_ID);

        Seller seller = Seller.builder()
                .user(user)
                .shopName("테스트샵")
                .businessNumber("1234567890")
                .build();
        seller.approve();
        ReflectionTestUtils.setField(seller, "id", sellerId);
        return seller;
    }

    private Product createProduct(Seller seller, ProductStatus status) {
        Product product = Product.builder()
                .seller(seller)
                .name("테스트상품")
                .thumbnailUrl("url1")
                .build();
        switch (status) {
            case APPROVED -> product.approve();
            case REJECTED -> product.reject();
            case DISCONTINUED -> product.discontinue();
            case FORCE_INACTIVE -> product.forceInactive();
            case APPROVE_REQUESTED -> {
                // 생성 시 기본값이므로 별도 처리 없음
            }
        }
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        return product;
    }

    private ProductImage createImage(Long id, Product product, int displayOrder, String url) {
        ProductImage image = ProductImage.builder()
                .product(product)
                .imageUrl(url)
                .displayOrder(displayOrder)
                .build();
        ReflectionTestUtils.setField(image, "id", id);
        return image;
    }

    @Nested
    @DisplayName("deleteImage - 상품 이미지 삭제")
    class DeleteImage {

        @Test
        @DisplayName("대표가 아닌 이미지를 삭제하면 남은 이미지가 재정렬되고 썸네일은 유지된다")
        void deleteImage_nonThumbnail_reordersAndKeepsThumbnail() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            Product product = createProduct(seller, ProductStatus.APPROVED);
            ProductImage img1 = createImage(1L, product, 1, "url1");
            ProductImage img2 = createImage(2L, product, 2, "url2");
            ProductImage img3 = createImage(3L, product, 3, "url3");

            given(sellerRepository.findByUserId(SELLER_USER_ID)).willReturn(Optional.of(seller));
            given(productRepository.findByIdForImageUpdate(PRODUCT_ID))
                    .willReturn(Optional.of(product));
            given(productImageRepository.findByProductIdAndStatusOrderByDisplayOrderAsc(
                    PRODUCT_ID, ProductImageStatus.ACTIVE))
                    .willReturn(List.of(img1, img2, img3));

            // when
            ProductImageDeleteResponse response =
                    sellerProductService.deleteImage(SELLER_USER_ID, PRODUCT_ID, 3L);

            // then
            assertThat(response.getDeletedImageId()).isEqualTo(3L);
            assertThat(response.getRemainingImageCount()).isEqualTo(2);
            assertThat(response.getThumbnailUrl()).isEqualTo("url1");
            assertThat(img3.isActive()).isFalse();
            assertThat(img1.getDisplayOrder()).isEqualTo(1);
            assertThat(img2.getDisplayOrder()).isEqualTo(2);
            assertThat(product.getThumbnailUrl()).isEqualTo("url1");
        }

        @Test
        @DisplayName("대표 이미지를 삭제하면 다음 이미지가 썸네일로 승격된다")
        void deleteImage_thumbnail_promotesNextImage() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            Product product = createProduct(seller, ProductStatus.APPROVED);
            ProductImage img1 = createImage(1L, product, 1, "url1");
            ProductImage img2 = createImage(2L, product, 2, "url2");
            ProductImage img3 = createImage(3L, product, 3, "url3");

            given(sellerRepository.findByUserId(SELLER_USER_ID)).willReturn(Optional.of(seller));
            given(productRepository.findByIdForImageUpdate(PRODUCT_ID))
                    .willReturn(Optional.of(product));
            given(productImageRepository.findByProductIdAndStatusOrderByDisplayOrderAsc(
                    PRODUCT_ID, ProductImageStatus.ACTIVE))
                    .willReturn(List.of(img1, img2, img3));

            // when
            ProductImageDeleteResponse response =
                    sellerProductService.deleteImage(SELLER_USER_ID, PRODUCT_ID, 1L);

            // then
            assertThat(response.getThumbnailUrl()).isEqualTo("url2");
            assertThat(img1.isActive()).isFalse();
            assertThat(img2.getDisplayOrder()).isEqualTo(1);
            assertThat(img3.getDisplayOrder()).isEqualTo(2);
            assertThat(product.getThumbnailUrl()).isEqualTo("url2");
        }

        @Test
        @DisplayName("FORCE_INACTIVE 상품의 이미지는 삭제할 수 있다")
        void deleteImage_forceInactiveProduct_succeeds() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            Product product = createProduct(seller, ProductStatus.FORCE_INACTIVE);
            ProductImage img1 = createImage(1L, product, 1, "url1");
            ProductImage img2 = createImage(2L, product, 2, "url2");

            given(sellerRepository.findByUserId(SELLER_USER_ID)).willReturn(Optional.of(seller));
            given(productRepository.findByIdForImageUpdate(PRODUCT_ID))
                    .willReturn(Optional.of(product));
            given(productImageRepository.findByProductIdAndStatusOrderByDisplayOrderAsc(
                    PRODUCT_ID, ProductImageStatus.ACTIVE))
                    .willReturn(List.of(img1, img2));

            // when
            ProductImageDeleteResponse response =
                    sellerProductService.deleteImage(SELLER_USER_ID, PRODUCT_ID, 2L);

            // then
            assertThat(response.getRemainingImageCount()).isEqualTo(1);
            assertThat(img2.isActive()).isFalse();
        }

        @Test
        @DisplayName("마지막 1장 남은 이미지는 삭제할 수 없어 PRODUCT_005 예외가 발생한다")
        void deleteImage_lastImage_throwsProduct005() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            Product product = createProduct(seller, ProductStatus.APPROVED);
            ProductImage img1 = createImage(1L, product, 1, "url1");

            given(sellerRepository.findByUserId(SELLER_USER_ID)).willReturn(Optional.of(seller));
            given(productRepository.findByIdForImageUpdate(PRODUCT_ID))
                    .willReturn(Optional.of(product));
            given(productImageRepository.findByProductIdAndStatusOrderByDisplayOrderAsc(
                    PRODUCT_ID, ProductImageStatus.ACTIVE))
                    .willReturn(List.of(img1));

            // when & then
            assertThatThrownBy(
                    () -> sellerProductService.deleteImage(SELLER_USER_ID, PRODUCT_ID, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_005);
        }

        @Test
        @DisplayName("상품에 속하지 않는 imageId면 PRODUCT_011 예외가 발생한다")
        void deleteImage_imageNotFound_throwsProduct011() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            Product product = createProduct(seller, ProductStatus.APPROVED);
            ProductImage img1 = createImage(1L, product, 1, "url1");
            ProductImage img2 = createImage(2L, product, 2, "url2");

            given(sellerRepository.findByUserId(SELLER_USER_ID)).willReturn(Optional.of(seller));
            given(productRepository.findByIdForImageUpdate(PRODUCT_ID))
                    .willReturn(Optional.of(product));
            given(productImageRepository.findByProductIdAndStatusOrderByDisplayOrderAsc(
                    PRODUCT_ID, ProductImageStatus.ACTIVE))
                    .willReturn(List.of(img1, img2));

            // when & then
            assertThatThrownBy(
                    () -> sellerProductService.deleteImage(SELLER_USER_ID, PRODUCT_ID, 999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_011);
        }

        @Test
        @DisplayName("다른 판매자의 상품 이미지면 PRODUCT_008 예외가 발생한다")
        void deleteImage_otherSellerProduct_throwsProduct008() {
            // given
            Seller requester = approvedSeller(SELLER_ID);
            Seller owner = approvedSeller(OTHER_SELLER_ID);
            Product product = createProduct(owner, ProductStatus.APPROVED);

            given(sellerRepository.findByUserId(SELLER_USER_ID)).willReturn(Optional.of(requester));
            given(productRepository.findByIdForImageUpdate(PRODUCT_ID))
                    .willReturn(Optional.of(product));

            // when & then
            assertThatThrownBy(
                    () -> sellerProductService.deleteImage(SELLER_USER_ID, PRODUCT_ID, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_008);
        }

        @Test
        @DisplayName("productId에 해당하는 상품이 없으면 PRODUCT_001 예외가 발생한다")
        void deleteImage_productNotFound_throwsProduct001() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            given(sellerRepository.findByUserId(SELLER_USER_ID)).willReturn(Optional.of(seller));
            given(productRepository.findByIdForImageUpdate(PRODUCT_ID))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(
                    () -> sellerProductService.deleteImage(SELLER_USER_ID, PRODUCT_ID, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_001);
        }

        @Test
        @DisplayName("DISCONTINUED 상품의 이미지는 삭제할 수 없어 PRODUCT_010 예외가 발생한다")
        void deleteImage_discontinuedProduct_throwsProduct010() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            Product product = createProduct(seller, ProductStatus.DISCONTINUED);

            given(sellerRepository.findByUserId(SELLER_USER_ID)).willReturn(Optional.of(seller));
            given(productRepository.findByIdForImageUpdate(PRODUCT_ID))
                    .willReturn(Optional.of(product));

            // when & then
            assertThatThrownBy(
                    () -> sellerProductService.deleteImage(SELLER_USER_ID, PRODUCT_ID, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_010);
        }
    }
}
