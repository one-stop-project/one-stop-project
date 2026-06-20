package com.sparta.one_stop.domain.product.service;

import com.sparta.one_stop.domain.product.dto.request.ProductCreateRequest;
import com.sparta.one_stop.domain.product.dto.request.ProductItemCreateRequest;
import com.sparta.one_stop.domain.product.dto.request.ProductUpdateRequest;
import com.sparta.one_stop.domain.product.dto.response.ProductCreateResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductDeleteResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductDetailResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductImageAddResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductImageDeleteResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductImageResponse;
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
import com.sparta.one_stop.global.storage.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerProductService {

    // 상품 삭제 차단 기준: 종료 상태(DELIVERED/CANCELLED/REJECTED)가 아닌 진행 중 주문
    private static final List<OrderItemStatus> ACTIVE_ORDER_ITEM_STATUSES = List.of(
        OrderItemStatus.PENDING_PAYMENT,
        OrderItemStatus.ORDERED,
        OrderItemStatus.CONFIRMED,
        OrderItemStatus.SHIPPING
    );
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final SellerRepository sellerRepository;
    private final ImageStorage imageStorage;

    // 상품 등록 (APPROVE_REQUESTED 상태로 생성)
    @Transactional
    public ProductCreateResponse create(Long sellerId, ProductCreateRequest request, List<MultipartFile> images) {
        // 1. 승인된 판매자인지 검증
        Seller seller = findApprovedSeller(sellerId);

        // 2. 카테고리 검증
        List<Category> categories = findAndValidateCategories(request.getCategoryIds());

        // 3. 옵션 조합 중복 검증
        validateOptionCombinations(request.getItems());

        // 4. 이미지 파일 저장 (1~10장) → 저장 URL 확보 (첫 장이 썸네일)
        validateImageCount(images, 0);
        List<String> imageUrls = storeImages(images);
        deleteStoredImagesOnRollback(imageUrls);  // DB 저장 실패 시 방금 쓴 파일 정리

        // 5. Product 엔티티 생성 (썸네일 = 첫 이미지)
        Product product = buildProduct(seller, request, imageUrls.get(0));

        // 6. 자식 엔티티들 매달기
        attachCategoryMappings(product, categories);
        attachImages(product, imageUrls);
        attachItems(product, request.getItems());
        if (request.getTags() != null) {
            product.replaceTags(new java.util.HashSet<>(request.getTags()));
        }

        // 7. 저장 (cascade로 자식들 같이 저장)
        Product saved = productRepository.save(product);

        // 8. 응답 변환
        return ProductCreateResponse.from(saved);
    }

    // 상품 목록 조회 (판매자 본인)
    public Page<SellerProductListResponse> getMyProducts(Long userId, Pageable pageable) {
        Seller seller = sellerRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.SELLER_001));

        Page<Product> products = productRepository.findAllBySellerId(seller.getId(), pageable);
        return SellerProductListResponse.from(products);
    }

    // 상품 단건 상세 조회 (판매자 본인)
    // 구매자용 상세와 달리 미승인(APPROVE_REQUESTED) 상품·판매중단(STOP) 옵션·재고를 모두 노출한다.
    public ProductDetailResponse getMyProductDetail(Long userId, Long productId) {
        Seller seller = sellerRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.SELLER_001));

        Product product = productRepository.findWithCollectionsById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new CustomException(ErrorCode.PRODUCT_008, "다른 판매자의 상품은 조회할 수 없습니다");
        }

        return ProductDetailResponse.from(product);
    }

    // 상품 수정
    @Transactional
    @CacheEvict(value = "productDetail", key = "#productId", cacheManager = "redisCacheManager")
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

        // 심사 대상 정보(이름·설명·썸네일)가 "실제로" 바뀌었는지 추적 — 값이 바뀐 수정만 재승인 대상 (같은 값 재전송은 재승인 안 함)
        // product.update() 호출 전에 비교해야 기존 값과 대조 가능
        boolean changed =
            isChanged(request.getName(), product.getName())
                || isChanged(request.getDescription(), product.getDescription())
                || isChanged(request.getThumbnailUrl(), product.getThumbnailUrl());

        // 기본 정보 업데이트 (null이면 기존 값 유지)
        product.update(request.getName(), request.getDescription(), request.getThumbnailUrl());

        // 카테고리 교체 (요청에 포함된 경우에만) — 집합이 실제로 달라졌을 때만 변경으로 간주
        if (request.getCategoryIds() != null) {
            List<Category> categories = findAndValidateCategories(request.getCategoryIds());
            Set<Long> currentIds = new HashSet<>();
            for (ProductCategoryMapping mapping : product.getCategoryMappings()) {
                currentIds.add(mapping.getCategory().getId());
            }
            if (!currentIds.equals(new HashSet<>(request.getCategoryIds()))) {
                changed = true;
            }
            product.getCategoryMappings().clear();
            attachCategoryMappings(product, categories);
        }

        // 태그 교체 (요청에 포함된 경우에만, 빈 리스트면 전체 삭제)
        if (request.getTags() != null) {
            product.replaceTags(new java.util.HashSet<>(request.getTags()));
        }

        // 이름·설명·이미지·카테고리가 바뀐 수정이면 재심사 대상으로 전환 (REJECTED·FORCE_INACTIVE도 동일하게 APPROVE_REQUESTED로)
        if (changed) {
            markForReapproval(product);
        }

        return ProductDetailResponse.from(product);
    }

    // 상품 삭제
    @Transactional
    @CacheEvict(value = "productDetail", key = "#productId", cacheManager = "redisCacheManager")
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
    @CacheEvict(value = "productDetail", key = "#productId", cacheManager = "redisCacheManager")
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
        // 저장소의 실제 파일은 DB 커밋 이후 비동기로 정리 (정책: 객체 삭제는 비동기) 롤백 시 파일은 보존
        deleteStoredImageAfterCommit(target.getImageUrl());

        // 남은 이미지 재정렬
        List<ProductImage> remaining = activeImages.stream()
            .filter(image -> !image.getId().equals(imageId))
            .toList();
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).updateDisplayOrder(i + 1);
        }

        // 대표 이미지(display_order=1) URL을 상품 썸네일에 동기화
        product.changeThumbnailUrl(remaining.get(0).getImageUrl());

        // 이미지 변경 → 재승인
        markForReapproval(product);

        return ProductImageDeleteResponse.builder()
            .deletedImageId(imageId)
            .remainingImageCount(remaining.size())
            .thumbnailUrl(product.getThumbnailUrl())
            .build();
    }

    // 상품 이미지 추가
    @Transactional
    @CacheEvict(value = "productDetail", key = "#productId", cacheManager = "redisCacheManager")
    public ProductImageAddResponse addImages(Long userId, Long productId, List<MultipartFile> images) {
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

        // 신규 1장 이상 + 기존+신규 최대 10장 검증 후 파일 저장
        validateImageCount(images, activeImages.size());
        List<String> imageUrls = storeImages(images);
        deleteStoredImagesOnRollback(imageUrls);  // DB 저장 실패 시 방금 쓴 파일 정리

        // 기존 이미지 순서(1,2,3...)는 그대로 두고, 새 이미지는 맨 뒤에 이어 붙임
        int nextOrder = activeImages.size();
        List<ProductImage> addedImages = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            ProductImage image = ProductImage.builder()
                .product(product)
                .imageUrl(imageUrls.get(i))
                .displayOrder(nextOrder + i + 1)
                .build();
            product.addProductImage(image);
            addedImages.add(image);
        }

        // 이미지 변경 → 재승인
        markForReapproval(product);

        // 응답에 새 이미지의 생성된 imageId를 담기 위해 즉시 저장(IDENTITY)해 식별자를 채운다.
        productImageRepository.saveAll(addedImages);

        return ProductImageAddResponse.builder()
            .addedImageCount(imageUrls.size())
            .totalImageCount(activeImages.size() + imageUrls.size())
            .thumbnailUrl(product.getThumbnailUrl())
            .addedImages(addedImages.stream().map(ProductImageResponse::from).toList())
            .build();
    }

    // 대표 이미지(썸네일) 변경 (선택 이미지를 display_order=1로 승격 + 재정렬 + 썸네일 동기화)
    @Transactional
    @CacheEvict(value = "productDetail", key = "#productId", cacheManager = "redisCacheManager")
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

        // 재승인 판정용 — 동기화 전 썸네일 URL 보관 (대표 위치가 아니라 실제 URL 변경 여부로 판단)
        String previousThumbnailUrl = product.getThumbnailUrl();

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

        // 썸네일 URL이 실제로 바뀐 경우에만 재승인 대상
        if (!Objects.equals(previousThumbnailUrl, product.getThumbnailUrl())) {
            markForReapproval(product);
        }

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

    // 심사 대상 정보 변경 시 재승인 처리
    // 이미 심사 대기(APPROVE_REQUESTED)면 그대로 두고, APPROVED/REJECTED/FORCE_INACTIVE는 재심사로 되돌린다.
    // (가격·재고는 옵션 수정(SellerItemService)에서 처리하며 상태를 바꾸지 않음 = 즉시 반영)
    private void markForReapproval(Product product) {
        if (product.getStatus() != ProductStatus.APPROVE_REQUESTED) {
            product.resubmit();
        }
    }

    // 요청값이 있고(non-null) 기존 값과 다르면 "변경"으로 판정 (null = 미변경 의도)
    private boolean isChanged(String requestValue, String currentValue) {
        return requestValue != null && !requestValue.equals(currentValue);
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
                throw new CustomException(ErrorCode.PRODUCT_016);
            }
        }
    }

    // Product 엔티티 빌드
    private Product buildProduct(Seller seller, ProductCreateRequest request, String thumbnailUrl) {
        return Product.builder()
            .seller(seller)
            .name(request.getName())
            .description(request.getDescription())
            .thumbnailUrl(thumbnailUrl)
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

    // 이미지 장수 검증: 신규 1장 이상 + 기존+신규 최대 10장
    private void validateImageCount(List<MultipartFile> files, int existingCount) {
        if (files == null || files.isEmpty()) {
            throw new CustomException(ErrorCode.PRODUCT_005);
        }
        if (existingCount + files.size() > 10) {
            throw new CustomException(ErrorCode.PRODUCT_006);
        }
    }

    // 업로드 파일을 저장소에 저장하고 저장 URL 목록 반환 (형식 검증은 ImageStorage가 수행)
    private List<String> storeImages(List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            // 내용이 빈(0바이트) 파일은 유효한 이미지가 아니므로 거부
            if (file == null || file.isEmpty()) {
                throw new CustomException(ErrorCode.COMMON_006);
            }
            try {
                urls.add(imageStorage.store(file.getBytes(), file.getContentType()));
            } catch (IOException e) {
                throw new CustomException(ErrorCode.COMMON_007);
            }
        }
        return urls;
    }

    // 저장 객체 삭제는 DB 커밋 이후에만 수행 (롤백 시 파일 보존). 실제 삭제는 ImageStorage가 비동기 처리
    private void deleteStoredImageAfterCommit(String url) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            imageStorage.delete(url);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                imageStorage.delete(url);
            }
        });
    }

    // 방금 저장한 파일을 트랜잭션 롤백 시 정리 (DB 저장 실패로 인한 orphan 파일 방지)
    private void deleteStoredImagesOnRollback(List<String> urls) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    urls.forEach(imageStorage::delete);
                }
            }
        });
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
