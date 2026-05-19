package com.sparta.one_stop.domain.product.service;

import com.sparta.one_stop.domain.product.dto.request.ProductCreateRequest;
import com.sparta.one_stop.domain.product.dto.request.ProductItemCreateRequest;
import com.sparta.one_stop.domain.product.dto.response.ProductCreateResponse;
import com.sparta.one_stop.domain.product.entity.Category;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductCategoryMapping;
import com.sparta.one_stop.domain.product.entity.ProductImage;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.CategoryRepository;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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


    private Seller findApprovedSeller(Long userId) {
        Seller seller = sellerRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.SELLER_001));

        if (seller.getStatus() != SellerStatus.APPROVED) {
            throw new CustomException(ErrorCode.SELLER_003);
        }
        return seller;
    }

    private List<Category> findAndValidateCategories(List<Long> categoryIds) {
        List<Category> categories = categoryRepository.findAllByIdIn(categoryIds);

        if (categories.size() != categoryIds.size()) {
            throw new CustomException(ErrorCode.PRODUCT_007);
        }
        return categories;
    }

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

    private void attachCategoryMappings(Product product, List<Category> categories) {
        for (Category category : categories) {
            ProductCategoryMapping mapping = ProductCategoryMapping.builder()
                .product(product)
                .category(category)
                .build();
            product.addCategoryMapping(mapping);
        }
    }

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
