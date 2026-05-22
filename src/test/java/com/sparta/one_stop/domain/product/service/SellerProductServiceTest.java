package com.sparta.one_stop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sparta.one_stop.domain.product.dto.request.ProductImageAddRequest;
import com.sparta.one_stop.domain.product.dto.response.ProductImageAddResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductImageDeleteResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductImageThumbnailResponse;
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
import org.springframework.beans.BeanUtils;
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

    // ===== 객체 생성 헬퍼 =====

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

    private ProductImageAddRequest addRequest(List<String> imageUrls) {
        ProductImageAddRequest request = BeanUtils.instantiateClass(ProductImageAddRequest.class);
        ReflectionTestUtils.setField(request, "imageUrls", imageUrls);
        return request;
    }

    private List<Integer> displayOrdersOf(Product product) {
        return product.getProductImages().stream()
                .map(ProductImage::getDisplayOrder)
                .sorted()
                .toList();
    }

    // ===== 목 세팅 헬퍼 =====

    private void mockSeller(Seller seller) {
        given(sellerRepository.findByUserId(SELLER_USER_ID)).willReturn(Optional.of(seller));
    }

    private void mockSellerAndProduct(Seller seller, Product product) {
        given(sellerRepository.findByUserId(SELLER_USER_ID)).willReturn(Optional.of(seller));
        given(productRepository.findByIdForImageUpdate(PRODUCT_ID)).willReturn(Optional.of(product));
    }

    private void mockActiveImages(List<ProductImage> images) {
        given(productImageRepository.findByProductIdAndStatusOrderByDisplayOrderAsc(
                PRODUCT_ID, ProductImageStatus.ACTIVE)).willReturn(images);
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
            mockSellerAndProduct(seller, product);
            mockActiveImages(List.of(img1, img2, img3));

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
            mockSellerAndProduct(seller, product);
            mockActiveImages(List.of(img1, img2, img3));

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
            mockSellerAndProduct(seller, product);
            mockActiveImages(List.of(img1, img2));

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
            mockSellerAndProduct(seller, product);
            mockActiveImages(List.of(img1));

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
            mockSellerAndProduct(seller, product);
            mockActiveImages(List.of(img1, img2));

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
            mockSellerAndProduct(requester, product);

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
            mockSeller(seller);
            given(productRepository.findByIdForImageUpdate(PRODUCT_ID)).willReturn(Optional.empty());

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
            mockSellerAndProduct(seller, product);

            // when & then
            assertThatThrownBy(
                    () -> sellerProductService.deleteImage(SELLER_USER_ID, PRODUCT_ID, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_010);
        }
    }

    @Nested
    @DisplayName("addImages - 상품 이미지 추가")
    class AddImages {

        @Test
        @DisplayName("이미지를 추가하면 마지막 display_order 뒤에 이어붙고 썸네일은 유지된다")
        void addImages_appendsAfterLastOrder() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            Product product = createProduct(seller, ProductStatus.APPROVED);
            ProductImage img1 = createImage(1L, product, 1, "url1");
            ProductImage img2 = createImage(2L, product, 2, "url2");
            mockSellerAndProduct(seller, product);
            mockActiveImages(List.of(img1, img2));

            // when
            ProductImageAddResponse response = sellerProductService.addImages(
                    SELLER_USER_ID, PRODUCT_ID, addRequest(List.of("url3", "url4")));

            // then
            assertThat(response.getAddedImageCount()).isEqualTo(2);
            assertThat(response.getTotalImageCount()).isEqualTo(4);
            assertThat(response.getThumbnailUrl()).isEqualTo("url1");
            assertThat(displayOrdersOf(product)).containsExactly(3, 4);
        }

        @Test
        @DisplayName("추가 후 ACTIVE 이미지가 10장을 초과하면 PRODUCT_006 예외가 발생한다")
        void addImages_exceedsMax_throwsProduct006() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            Product product = createProduct(seller, ProductStatus.APPROVED);
            List<ProductImage> nineImages = new java.util.ArrayList<>();
            for (int i = 1; i <= 9; i++) {
                nineImages.add(createImage((long) i, product, i, "url" + i));
            }
            mockSellerAndProduct(seller, product);
            mockActiveImages(nineImages);

            // when & then (9장 + 2장 = 11장)
            assertThatThrownBy(() -> sellerProductService.addImages(
                    SELLER_USER_ID, PRODUCT_ID, addRequest(List.of("url10", "url11"))))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_006);
        }

        @Test
        @DisplayName("FORCE_INACTIVE 상품에도 이미지를 추가할 수 있다")
        void addImages_forceInactiveProduct_succeeds() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            Product product = createProduct(seller, ProductStatus.FORCE_INACTIVE);
            ProductImage img1 = createImage(1L, product, 1, "url1");
            mockSellerAndProduct(seller, product);
            mockActiveImages(List.of(img1));

            // when
            ProductImageAddResponse response = sellerProductService.addImages(
                    SELLER_USER_ID, PRODUCT_ID, addRequest(List.of("url2")));

            // then
            assertThat(response.getTotalImageCount()).isEqualTo(2);
            assertThat(displayOrdersOf(product)).containsExactly(2);
        }

        @Test
        @DisplayName("다른 판매자의 상품이면 PRODUCT_008 예외가 발생한다")
        void addImages_otherSellerProduct_throwsProduct008() {
            // given
            Seller requester = approvedSeller(SELLER_ID);
            Seller owner = approvedSeller(OTHER_SELLER_ID);
            Product product = createProduct(owner, ProductStatus.APPROVED);
            mockSellerAndProduct(requester, product);

            // when & then
            assertThatThrownBy(() -> sellerProductService.addImages(
                    SELLER_USER_ID, PRODUCT_ID, addRequest(List.of("url2"))))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_008);
        }

        @Test
        @DisplayName("productId에 해당하는 상품이 없으면 PRODUCT_001 예외가 발생한다")
        void addImages_productNotFound_throwsProduct001() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            mockSeller(seller);
            given(productRepository.findByIdForImageUpdate(PRODUCT_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> sellerProductService.addImages(
                    SELLER_USER_ID, PRODUCT_ID, addRequest(List.of("url2"))))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_001);
        }

        @Test
        @DisplayName("DISCONTINUED 상품에는 이미지를 추가할 수 없어 PRODUCT_010 예외가 발생한다")
        void addImages_discontinuedProduct_throwsProduct010() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            Product product = createProduct(seller, ProductStatus.DISCONTINUED);
            mockSellerAndProduct(seller, product);

            // when & then
            assertThatThrownBy(() -> sellerProductService.addImages(
                    SELLER_USER_ID, PRODUCT_ID, addRequest(List.of("url2"))))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_010);
        }
    }

    @Nested
    @DisplayName("changeThumbnail - 대표 이미지(썸네일) 변경")
    class ChangeThumbnail {

        @Test
        @DisplayName("선택 이미지가 display_order=1로 승격되고 나머지가 재정렬되며 썸네일이 동기화된다")
        void changeThumbnail_promotesAndReorders() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            Product product = createProduct(seller, ProductStatus.APPROVED);
            ProductImage img1 = createImage(1L, product, 1, "url1");
            ProductImage img2 = createImage(2L, product, 2, "url2");
            ProductImage img3 = createImage(3L, product, 3, "url3");
            mockSellerAndProduct(seller, product);
            mockActiveImages(List.of(img1, img2, img3));

            // when
            ProductImageThumbnailResponse response =
                    sellerProductService.changeThumbnail(SELLER_USER_ID, PRODUCT_ID, 3L);

            // then
            assertThat(response.getThumbnailImageId()).isEqualTo(3L);
            assertThat(response.getThumbnailUrl()).isEqualTo("url3");
            assertThat(img3.getDisplayOrder()).isEqualTo(1);
            assertThat(img1.getDisplayOrder()).isEqualTo(2);
            assertThat(img2.getDisplayOrder()).isEqualTo(3);
            assertThat(product.getThumbnailUrl()).isEqualTo("url3");
        }

        @Test
        @DisplayName("이미 대표인 이미지를 다시 지정해도 순서가 유지된다")
        void changeThumbnail_alreadyThumbnail_isIdempotent() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            Product product = createProduct(seller, ProductStatus.APPROVED);
            ProductImage img1 = createImage(1L, product, 1, "url1");
            ProductImage img2 = createImage(2L, product, 2, "url2");
            mockSellerAndProduct(seller, product);
            mockActiveImages(List.of(img1, img2));

            // when
            ProductImageThumbnailResponse response =
                    sellerProductService.changeThumbnail(SELLER_USER_ID, PRODUCT_ID, 1L);

            // then
            assertThat(response.getThumbnailUrl()).isEqualTo("url1");
            assertThat(img1.getDisplayOrder()).isEqualTo(1);
            assertThat(img2.getDisplayOrder()).isEqualTo(2);
            assertThat(product.getThumbnailUrl()).isEqualTo("url1");
        }

        @Test
        @DisplayName("상품에 속하지 않는 imageId면 PRODUCT_011 예외가 발생한다")
        void changeThumbnail_imageNotFound_throwsProduct011() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            Product product = createProduct(seller, ProductStatus.APPROVED);
            ProductImage img1 = createImage(1L, product, 1, "url1");
            mockSellerAndProduct(seller, product);
            mockActiveImages(List.of(img1));

            // when & then
            assertThatThrownBy(() -> sellerProductService.changeThumbnail(
                    SELLER_USER_ID, PRODUCT_ID, 999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_011);
        }

        @Test
        @DisplayName("다른 판매자의 상품이면 PRODUCT_008 예외가 발생한다")
        void changeThumbnail_otherSellerProduct_throwsProduct008() {
            // given
            Seller requester = approvedSeller(SELLER_ID);
            Seller owner = approvedSeller(OTHER_SELLER_ID);
            Product product = createProduct(owner, ProductStatus.APPROVED);
            mockSellerAndProduct(requester, product);

            // when & then
            assertThatThrownBy(() -> sellerProductService.changeThumbnail(
                    SELLER_USER_ID, PRODUCT_ID, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_008);
        }

        @Test
        @DisplayName("productId에 해당하는 상품이 없으면 PRODUCT_001 예외가 발생한다")
        void changeThumbnail_productNotFound_throwsProduct001() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            mockSeller(seller);
            given(productRepository.findByIdForImageUpdate(PRODUCT_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> sellerProductService.changeThumbnail(
                    SELLER_USER_ID, PRODUCT_ID, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_001);
        }

        @Test
        @DisplayName("DISCONTINUED 상품의 썸네일은 변경할 수 없어 PRODUCT_010 예외가 발생한다")
        void changeThumbnail_discontinuedProduct_throwsProduct010() {
            // given
            Seller seller = approvedSeller(SELLER_ID);
            Product product = createProduct(seller, ProductStatus.DISCONTINUED);
            mockSellerAndProduct(seller, product);

            // when & then
            assertThatThrownBy(() -> sellerProductService.changeThumbnail(
                    SELLER_USER_ID, PRODUCT_ID, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_010);
        }
    }
}
