package com.sparta.one_stop.domain.product.service;

import com.sparta.one_stop.domain.product.dto.request.ProductCreateRequest;
import com.sparta.one_stop.domain.product.dto.request.ProductItemCreateRequest;
import com.sparta.one_stop.domain.product.dto.request.ProductUpdateRequest;
import com.sparta.one_stop.domain.product.dto.response.ProductCreateResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductDeleteResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductDetailResponse;
import com.sparta.one_stop.domain.product.dto.response.SellerProductListResponse;
import com.sparta.one_stop.domain.product.entity.Category;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductCategoryMapping;
import com.sparta.one_stop.domain.product.entity.ProductImage;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.CategoryRepository;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
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
    private final CategoryRepository categoryRepository;
    private final SellerRepository sellerRepository;

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

        // 정책: DISCONTINUED / FORCE_INACTIVE 상태는 수정 불가
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

        // 정책: REJECTED 상태에서 수정 시 APPROVE_REQUESTED로 재전환
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

        product.discontinue();

        return ProductDeleteResponse.from(product);
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

    // 카테고리 조회 + 존재 검증
    private List<Category> findAndValidateCategories(List<Long> categoryIds) {
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
