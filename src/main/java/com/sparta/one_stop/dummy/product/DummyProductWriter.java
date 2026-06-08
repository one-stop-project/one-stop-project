package com.sparta.one_stop.dummy.product;

import com.sparta.one_stop.domain.product.entity.Category;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductCategoryMapping;
import com.sparta.one_stop.domain.product.entity.ProductImage;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.CategoryRepository;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.dummy.grouping.GroupedProduct;
import com.sparta.one_stop.dummy.grouping.ProductVariant;
import com.sparta.one_stop.dummy.source.DummyProductSourceGroup;
import com.sparta.one_stop.dummy.source.DummyProductSourceGroupRepository;
import com.sparta.one_stop.dummy.source.DummyProductSourceListing;
import com.sparta.one_stop.dummy.source.DummyProductSourceListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

// 그룹핑된 더미 상품을 우리 도메인 엔티티로 멱등 영속화.
// SellerProductService.create()의 엔티티 조립을 미러링하되, 파일 업로드 대신 사전 저장된 이미지 URL을 받고
// approve()를 직접 호출(정책 §12: 즉시 APPROVED, 관리자 API 미경유). 직접 SQL 아님(엔티티+repository).
@Component
@RequiredArgsConstructor
public class DummyProductWriter {

    private static final String SOURCE = "NAVER";
    private static final long DEFAULT_STOCK = 100L;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final DummyProductSourceGroupRepository sourceGroupRepository;
    private final DummyProductSourceListingRepository sourceListingRepository;

    // 그룹 1개 멱등 저장 (상품당 독립 트랜잭션 — 오케스트레이터가 그룹별로 호출, 한 건 실패가 전체를 롤백하지 않음)
    @Transactional
    public DummyWriteResult write(Seller seller, GroupedProduct grouped) {
        return sourceGroupRepository.findBySourceAndBaseSourceKey(SOURCE, grouped.baseSourceKey())
            .map(group -> {
                updateExisting(group, grouped);
                return DummyWriteResult.UPDATED;
            })
            .orElseGet(() -> {
                createNew(seller, grouped);
                return DummyWriteResult.CREATED;
            });
    }

    // 신규 그룹 → Product + 옵션(ProductItem) 생성, 즉시 승인, source 매핑 기록
    private void createNew(Seller seller, GroupedProduct grouped) {
        List<Category> categories = categoryRepository.findAllByIdIn(grouped.categoryIds());

        Product product = Product.builder()
            .seller(seller)
            .name(grouped.name())
            .description(grouped.description())
            .thumbnailUrl(grouped.thumbnailImageUrl())
            .optionName1(axisName(grouped, 0))
            .optionName2(axisName(grouped, 1))
            .optionName3(axisName(grouped, 2))
            .optionName4(axisName(grouped, 3))
            .optionName5(axisName(grouped, 4))
            .build();

        for (Category category : categories) {
            product.addCategoryMapping(ProductCategoryMapping.builder()
                .product(product)
                .category(category)
                .build());
        }

        // 1차 썸네일 1장 (정책 §9)
        product.addProductImage(ProductImage.builder()
            .product(product)
            .imageUrl(grouped.thumbnailImageUrl())
            .displayOrder(1)
            .build());

        // 변형 → ProductItem (객체 참조를 변형 순서대로 보관 → save 후 채워진 id로 listing 매핑)
        List<ProductItem> items = new ArrayList<>();
        for (ProductVariant variant : grouped.variants()) {
            ProductItem item = buildItem(product, variant);
            product.addProductItem(item);
            items.add(item);
        }

        product.approve();  // 정책 §12: 생성 즉시 APPROVED
        Product saved = productRepository.save(product);  // cascade로 자식 저장 + id 채워짐

        sourceGroupRepository.save(DummyProductSourceGroup.builder()
            .source(SOURCE)
            .baseSourceKey(grouped.baseSourceKey())
            .productId(saved.getId())
            .build());

        List<ProductVariant> variants = grouped.variants();
        for (int i = 0; i < variants.size(); i++) {
            saveListing(grouped.baseSourceKey(), saved.getId(), items.get(i), variants.get(i));
        }
    }

    // 기존 그룹 → 가격만 갱신 + 신규 변형 추가 (정책 §8: 재실행 시 가격 갱신)
    private void updateExisting(DummyProductSourceGroup group, GroupedProduct grouped) {
        Product product = productRepository.findById(group.getProductId()).orElse(null);
        if (product == null) {
            return;  // 상품이 삭제된 stale 그룹 — 더미 재생성 안 함
        }

        Map<String, DummyProductSourceListing> existing = sourceListingRepository
            .findAllBySourceAndBaseSourceKey(SOURCE, grouped.baseSourceKey()).stream()
            .collect(Collectors.toMap(DummyProductSourceListing::getListingSourceKey, l -> l, (a, b) -> a));

        List<ProductVariant> newVariants = new ArrayList<>();
        for (ProductVariant variant : grouped.variants()) {
            DummyProductSourceListing listing = existing.get(variant.listingSourceKey());
            if (listing == null) {
                newVariants.add(variant);  // 신규 변형
                continue;
            }
            // 기존 변형 → 가격 변동 시에만 갱신 (이미지·설명·옵션값은 재실행 시 건드리지 않음)
            if (!Objects.equals(listing.getLastSourcePrice(), variant.sourcePrice())) {
                findItem(product, listing.getItemId())
                    .ifPresent(item -> item.updateForAdjustment(variant.sourcePrice(), null, null));
                listing.updateLastSourcePrice(variant.sourcePrice());
            }
        }

        if (newVariants.isEmpty()) {
            return;
        }
        // 신규 변형 → ProductItem 추가
        List<ProductItem> addedItems = new ArrayList<>();
        for (ProductVariant variant : newVariants) {
            ProductItem item = buildItem(product, variant);
            product.addProductItem(item);
            addedItems.add(item);
        }
        productRepository.saveAndFlush(product);  // 새 ProductItem id 확보
        for (int i = 0; i < newVariants.size(); i++) {
            saveListing(grouped.baseSourceKey(), product.getId(), addedItems.get(i), newVariants.get(i));
        }
    }

    private void saveListing(String baseSourceKey, Long productId, ProductItem item, ProductVariant variant) {
        sourceListingRepository.save(DummyProductSourceListing.builder()
            .source(SOURCE)
            .listingSourceKey(variant.listingSourceKey())
            .baseSourceKey(baseSourceKey)
            .productId(productId)
            .itemId(item.getId())
            .lastSourcePrice(variant.sourcePrice())
            .variantSignature(item.getOptionSummary())
            .build());
    }

    private ProductItem buildItem(Product product, ProductVariant variant) {
        return ProductItem.builder()
            .product(product)
            .optionValue1(optionValueAt(variant, 0))
            .optionValue2(optionValueAt(variant, 1))
            .optionValue3(optionValueAt(variant, 2))
            .optionValue4(optionValueAt(variant, 3))
            .optionValue5(optionValueAt(variant, 4))
            .price(variant.sourcePrice())
            .stock(DEFAULT_STOCK)
            .build();
    }

    private Optional<ProductItem> findItem(Product product, Long itemId) {
        return product.getProductItems().stream()
            .filter(it -> Objects.equals(it.getId(), itemId))
            .findFirst();
    }

    private String axisName(GroupedProduct grouped, int idx) {
        List<String> axes = grouped.optionAxisNames();
        return (axes != null && idx < axes.size()) ? axes.get(idx) : null;
    }

    // ProductItem.optionValue는 NOT NULL이라 미사용 축은 빈 문자열로 채운다 (unique 제약 충돌 방지)
    private String optionValueAt(ProductVariant variant, int idx) {
        List<String> values = variant.optionValues();
        if (values == null || idx >= values.size() || values.get(idx) == null) {
            return "";
        }
        return values.get(idx);
    }
}
