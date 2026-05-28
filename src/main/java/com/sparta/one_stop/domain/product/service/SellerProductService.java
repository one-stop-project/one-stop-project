package com.sparta.one_stop.domain.product.service;

import com.sparta.one_stop.domain.product.dto.request.ProductCreateRequest;
import com.sparta.one_stop.domain.product.dto.request.ProductImageAddRequest;
import com.sparta.one_stop.domain.product.dto.request.ProductItemCreateRequest;
import com.sparta.one_stop.domain.product.dto.request.ProductUpdateRequest;
import com.sparta.one_stop.domain.product.dto.response.ProductCreateResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductDeleteResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductDetailResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductImageAddResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductImageDeleteResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductImageThumbnailResponse;
import com.sparta.one_stop.domain.product.dto.response.SellerProductListResponse;
import com.sparta.one_stop.domain.product.entity.Category;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductCategoryMapping;
import com.sparta.one_stop.domain.product.entity.ProductImage;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.CategoryRepository;
import com.sparta.one_stop.domain.product.repository.ProductImageRepository;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.enums.product.ProductImageStatus;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final SellerRepository sellerRepository;

    // 상품 삭제 차단 기준: 종료 상태(DELIVERED/CANCELLED/REJECTED)가 아닌 진행 중 주문
    private static final List<OrderItemStatus> ACTIVE_ORDER_ITEM_STATUSES = List.of(
        OrderItemStatus.PENDING_PAYMENT,
        OrderItemStatus.ORDERED,
        OrderItemStatus.CONFIRMED,
        OrderItemStatus.SHIPPING
    );

    // 상품 등록 (APPROVE_REQUESTED 상태로 생성)
    @Transactional
    public ProductCreateResponse create(Long sellerId, ProductCreateRequest request) {
        // 1. 승인된 판매자인지 검증
        Seller seller = findApprovedSeller(sellerId);

        // 2. 카테고리 검증
        List<Category> categories = findAndValidateCategories(request.getCategoryIds());

        // 3. 옵션 조합 중복 검증
        validateOptionCombinations(request.getItems());

        // 4. Product 엔티티 생성
        Product product = buildProduct(seller, request);

        // 5. 자식 엔티티들 매달기
        attachCategoryMappings(product, categories);
        attachImages(product, request.getImageUrls());
        attachItems(product, request.getItems());

        // 6. 저장 (cascade로 자식들 같이 저장)
        Product saved = productRepository.save(product);

        // 7. 응답 변환
        return ProductCreateResponse.from(saved);
    }

    // 상품 목록 조회 (판매자 본인)
    public Page<SellerProductListResponse> getMyProducts(Long userId, Pageable pageable) {
        Seller seller = sellerRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.SELLER_001));

        Page<Product> products = productRepository.findAllBySellerId(seller.getId(), pageable);
        return SellerProductListResponse.from(products);
    }

    // 상품 수정
    @Transactional
    public ProductDetailResponse update(Long userId, Long productId, ProductUpdateRequest request) {
        Seller seller = findApprovedSeller(userId);

        Product product = productRepository.findWithCollectionsById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new CustomException(ErrorCode.PRODUCT_008);
        }

        // DISCONTINUED 상태만 수정 불가 (FORCE_INACTIVE는 판매자 소명/재승인을 위해 수정 허용)
        if (!product.isEditable()) {
            throw new CustomException(ErrorCode.PRODUCT_010);
        }

        // 기본 정보 업데이트 (null이면 기존 값 유지)
        product.update(request.getName(), request.getDescription(), request.getThumbnailUrl());

        // 카테고리 교체 (요청에 포함된 경우에만)
        if (request.getCategoryIds() != null) {
            List<Category> categories = findAndValidateCategories(request.getCategoryIds());
            product.getCategoryMappings().clear();
            attachCategoryMappings(product, categories);
        }

        // REJECTED 상태에서 수정 시 APPROVE_REQUESTED로 재전환
        if (product.getStatus() == ProductStatus.REJECTED) {
            product.resubmit();
        }

        return ProductDetailResponse.from(product);
    }

    // 상품 삭제
    @Transactional
    public ProductDeleteResponse delete(Long userId, Long productId) {
        Seller seller = findApprovedSeller(userId);

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new CustomException(ErrorCode.PRODUCT_008);
        }

        // 진행 중 주문(PENDING_PAYMENT/ORDERED/CONFIRMED/SHIPPING) 존재 시 삭제 불가
        if (productRepository.existsActiveOrderItemByProductId(productId, ACTIVE_ORDER_ITEM_STATUSES)) {
            throw new CustomException(ErrorCode.PRODUCT_009);
        }

        product.discontinue();

        return ProductDeleteResponse.from(product);
    }

    // 상품 이미지 삭제 (Soft Delete + display_order 재정렬 + 썸네일 동기화)
    @Transactional
    public ProductImageDeleteResponse deleteImage(Long userId, Long productId, Long imageId) {
        Seller seller = findApprovedSeller(userId);

        // 낙관적 락 강제 증가 -> 같은 상품 이미지 동시 변경 직렬화
        Product product = productRepository.findByIdForImageUpdate(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new CustomException(ErrorCode.PRODUCT_008);
        }

        // DISCONTINUED 상태는 수정 불가 (FORCE_INACTIVE는 허용)
        if (!product.isEditable()) {
            throw new CustomException(ErrorCode.PRODUCT_010);
        }

        List<ProductImage> activeImages = productImageRepository
            .findByProductIdAndStatusOrderByDisplayOrderAsc(productId, ProductImageStatus.ACTIVE);

        ProductImage target = activeImages.stream()
            .filter(image -> image.getId().equals(imageId))
            .findFirst()
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_011));

        // 상품 이미지는 최소 1장 유지
        if (activeImages.size() <= 1) {
            throw new CustomException(ErrorCode.PRODUCT_005);
        }

        target.delete();

        // 남은 이미지 재정렬
        List<ProductImage> remaining = activeImages.stream()
            .filter(image -> !image.getId().equals(imageId))
            .toList();
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).updateDisplayOrder(i + 1);
        }

        // 대표 이미지(display_order=1) URL을 상품 썸네일에 동기화
        product.changeThumbnailUrl(remaining.get(0).getImageUrl());

        return ProductImageDeleteResponse.builder()
            .deletedImageId(imageId)
            .remainingImageCount(remaining.size())
            .thumbnailUrl(product.getThumbnailUrl())
            .build();
    }

    // 상품 이미지 추가
    @Transactional
    public ProductImageAddResponse addImages(Long userId, Long productId, ProductImageAddRequest request) {
        Seller seller = findApprovedSeller(userId);

        // 낙관적 락 강제 증가 -> 같은 상품 이미지 동시 변경 직렬화
        Product product = productRepository.findByIdForImageUpdate(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new CustomException(ErrorCode.PRODUCT_008);
        }

        // DISCONTINUED 상태는 수정 불가 (FORCE_INACTIVE는 허용)
        if (!product.isEditable()) {
            throw new CustomException(ErrorCode.PRODUCT_010);
        }

        List<ProductImage> activeImages = productImageRepository
            .findByProductIdAndStatusOrderByDisplayOrderAsc(productId, ProductImageStatus.ACTIVE);

        // 추가 후 ACTIVE 이미지가 최대 10장을 초과하면 거부
        List<String> imageUrls = request.getImageUrls();
        if (activeImages.size() + imageUrls.size() > 10) {
            throw new CustomException(ErrorCode.PRODUCT_006);
        }

        // 기존 이미지는 1..N 연속 정렬 유지 -> 마지막 순서 다음부터 배치
        int nextOrder = activeImages.size();
        for (int i = 0; i < imageUrls.size(); i++) {
            ProductImage image = ProductImage.builder()
                .product(product)
                .imageUrl(imageUrls.get(i))
                .displayOrder(nextOrder + i + 1)
                .build();
            product.addProductImage(image);
        }

        return ProductImageAddResponse.builder()
            .addedImageCount(imageUrls.size())
            .totalImageCount(activeImages.size() + imageUrls.size())
            .thumbnailUrl(product.getThumbnailUrl())
            .build();
    }

    // 대표 이미지(썸네일) 변경 (선택 이미지를 display_order=1로 승격 + 재정렬 + 썸네일 동기화)
    @Transactional
    public ProductImageThumbnailResponse changeThumbnail(Long userId, Long productId, Long imageId) {
        Seller seller = findApprovedSeller(userId);

        // 낙관적 락 강제 증가 -> 같은 상품 이미지 동시 변경 직렬화
        Product product = productRepository.findByIdForImageUpdate(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new CustomException(ErrorCode.PRODUCT_008);
        }

        // DISCONTINUED 상태는 수정 불가 (FORCE_INACTIVE는 허용)
        if (!product.isEditable()) {
            throw new CustomException(ErrorCode.PRODUCT_010);
        }

        List<ProductImage> activeImages = productImageRepository
            .findByProductIdAndStatusOrderByDisplayOrderAsc(productId, ProductImageStatus.ACTIVE);

        ProductImage target = activeImages.stream()
            .filter(image -> image.getId().equals(imageId))
            .findFirst()
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_011));

        // 선택 이미지를 대표로 승격, 나머지는 기존 상대 순서 유지
        target.updateDisplayOrder(1);
        int order = 2;
        for (ProductImage image : activeImages) {
            if (!image.getId().equals(imageId)) {
                image.updateDisplayOrder(order++);
            }
        }

        // 대표 이미지 URL을 상품 썸네일에 동기화
        product.changeThumbnailUrl(target.getImageUrl());

        return ProductImageThumbnailResponse.builder()
            .thumbnailImageId(target.getId())
            .thumbnailUrl(product.getThumbnailUrl())
            .build();
    }

    // 승인된 판매자 검증
    private Seller findApprovedSeller(Long userId) {
        Seller seller = sellerRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.SELLER_001));

        if (seller.getStatus() != SellerStatus.APPROVED) {
            throw new CustomException(ErrorCode.SELLER_003);
        }
        return seller;
    }

    // 카테고리 조회 + 존재 검증 + 매핑 개수 검증 (DTO @Size 외 방어 깊이)
    private List<Category> findAndValidateCategories(List<Long> categoryIds) {
        // 상품당 카테고리 매핑 최소 1개, 최대 3개
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new CustomException(ErrorCode.PRODUCT_012);
        }
        if (categoryIds.size() > 3) {
            throw new CustomException(ErrorCode.PRODUCT_013);
        }

        List<Category> categories = categoryRepository.findAllByIdIn(categoryIds);

        if (categories.size() != categoryIds.size()) {
            throw new CustomException(ErrorCode.PRODUCT_007);
        }
        return categories;
    }

    // 옵션값 조합 중복 검증
    private void validateOptionCombinations(List<ProductItemCreateRequest> items) {
        Set<String> keys = new HashSet<>();
        for (ProductItemCreateRequest item : items) {
            if (!keys.add(item.getOptionCombinationKey())) {
                throw new CustomException(
                    ErrorCode.COMMON_001,
                    "옵션 조합이 중복됩니다"
                );
            }
        }
    }

    // Product 엔티티 빌드
    private Product buildProduct(Seller seller, ProductCreateRequest request) {
        return Product.builder()
            .seller(seller)
            .name(request.getName())
            .description(request.getDescription())
            .thumbnailUrl(request.getThumbnailUrl())
            .optionName1(request.getOptionName(0))
            .optionName2(request.getOptionName(1))
            .optionName3(request.getOptionName(2))
            .optionName4(request.getOptionName(3))
            .optionName5(request.getOptionName(4))
            .build();
    }

    // 카테고리 매핑 자식 엔티티
    private void attachCategoryMappings(Product product, List<Category> categories) {
        for (Category category : categories) {
            ProductCategoryMapping mapping = ProductCategoryMapping.builder()
                .product(product)
                .category(category)
                .build();
            product.addCategoryMapping(mapping);
        }
    }

    // 이미지 자식 엔티티
    private void attachImages(Product product, List<String> imageUrls) {
        for (int i = 0; i < imageUrls.size(); i++) {
            ProductImage image = ProductImage.builder()
                .product(product)
                .imageUrl(imageUrls.get(i))
                .displayOrder(i + 1)  // 1-based: 첫 번째가 썸네일
                .build();
            product.addProductImage(image);
        }
    }

    // 옵션 자식 엔티티
    private void attachItems(Product product, List<ProductItemCreateRequest> itemRequests) {
        for (ProductItemCreateRequest req : itemRequests) {
            ProductItem item = ProductItem.builder()
                .product(product)
                .optionValue1(nullToEmpty(req.getOptionValue1()))
                .optionValue2(nullToEmpty(req.getOptionValue2()))
                .optionValue3(nullToEmpty(req.getOptionValue3()))
                .optionValue4(nullToEmpty(req.getOptionValue4()))
                .optionValue5(nullToEmpty(req.getOptionValue5()))
                .price(req.getPrice())
                .stock(req.getStock())
                .build();
            product.addProductItem(item);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
