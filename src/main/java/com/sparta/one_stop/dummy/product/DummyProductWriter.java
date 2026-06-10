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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// 그룹핑된 더미 상품을 우리 도메인 엔티티로 멱등 영속화.
// SellerProductService.create()의 엔티티 조립을 미러링하되, 파일 업로드 대신 사전 저장된 이미지 URL을 받고
// approve()를 직접 호출(정책 §12: 즉시 APPROVED, 관리자 API 미경유). 직접 SQL 아님(엔티티+repository).
@Component
@RequiredArgsConstructor
public class DummyProductWriter {

    private static final Logger log = LoggerFactory.getLogger(DummyProductWriter.class);
    private static final String SOURCE = "NAVER";
    private static final long DEFAULT_STOCK = 100L;
    private static final int MAX_OPTION_LEN = 100;  // ProductItem.optionValue / Product.optionName 컬럼 길이

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final DummyProductSourceGroupRepository sourceGroupRepository;
    private final DummyProductSourceListingRepository sourceListingRepository;

    // 그룹 1개 멱등 저장 (상품당 독립 트랜잭션 — 오케스트레이터가 그룹별로 호출, 한 건 실패가 전체를 롤백하지 않음)
    @Transactional
    public DummyWriteResult write(Seller seller, GroupedProduct grouped) {
        // 그룹키로 기존 그룹 조회. 없으면 이미 저장된 변형이 속한 그룹으로 라우팅한다.
        // (재실행 시 다변형↔단일 전환으로 그룹키가 바뀌어도 listing은 그대로 → 중복 상품·unique 충돌 방지)
        Optional<DummyProductSourceGroup> group =
            sourceGroupRepository.findBySourceAndBaseSourceKey(SOURCE, grouped.baseSourceKey());
        if (group.isEmpty()) {
            group = findGroupByExistingListing(grouped);
        }
        if (group.isPresent()) {
            boolean updated = updateExisting(group.get(), grouped);
            return updated ? DummyWriteResult.UPDATED : DummyWriteResult.SKIPPED;
        }
        createNew(seller, grouped);
        return DummyWriteResult.CREATED;
    }

    // 변형 중 하나라도 이미 저장돼 있으면 그 변형이 속한 기존 그룹을 반환
    private Optional<DummyProductSourceGroup> findGroupByExistingListing(GroupedProduct grouped) {
        for (ProductVariant variant : grouped.variants()) {
            Optional<DummyProductSourceGroup> group = sourceListingRepository
                .findBySourceAndListingSourceKey(SOURCE, variant.listingSourceKey())
                .flatMap(listing -> sourceGroupRepository
                    .findBySourceAndBaseSourceKey(SOURCE, listing.getBaseSourceKey()));
            if (group.isPresent()) {
                return group;
            }
        }
        return Optional.empty();
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
    // 반환: 실제 갱신했으면 true, 상품이 삭제된 stale 그룹이라 건너뛰면 false
    private boolean updateExisting(DummyProductSourceGroup group, GroupedProduct grouped) {
        Product product = productRepository.findById(group.getProductId()).orElse(null);
        if (product == null) {
            // 매핑은 있으나 상품이 삭제됨 — 더미 재생성 안 함. UPDATED 오보고 방지 위해 false 반환
            log.warn("[더미시드] stale 매핑(상품 없음) → 건너뜀 (productId={}, base={})",
                group.getProductId(), group.getBaseSourceKey());
            return false;
        }

        Map<String, DummyProductSourceListing> existing = sourceListingRepository
            .findAllBySourceAndBaseSourceKey(SOURCE, group.getBaseSourceKey()).stream()
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
            return true;  // 가격만 갱신(또는 변동 없음) — 상품 존재하므로 UPDATED
        }
        // 신규 변형 → ProductItem 추가. 단 기존 item과 옵션조합이 겹치면 uk_product_item_options 위반으로
        // 그룹 전체(가격갱신 포함)가 롤백되므로, 겹치는 조합은 추가하지 않고 건너뛴다.
        Set<String> combos = product.getProductItems().stream()
            .map(this::optionCombo)
            .collect(Collectors.toCollection(HashSet::new));
        List<ProductItem> addedItems = new ArrayList<>();
        List<ProductVariant> persisted = new ArrayList<>();
        for (ProductVariant variant : newVariants) {
            // 이 변형이 이미 다른 그룹에 저장돼 있으면(단일→다변형 전환 재실행) 재삽입 시 listing unique 위반 → 건너뜀
            if (sourceListingRepository.findBySourceAndListingSourceKey(SOURCE, variant.listingSourceKey()).isPresent()) {
                continue;
            }
            ProductItem item = buildItem(product, variant);
            if (!combos.add(optionCombo(item))) {
                continue;  // 동일 옵션조합 이미 존재 → 중복 추가 방지
            }
            product.addProductItem(item);
            addedItems.add(item);
            persisted.add(variant);
        }
        if (addedItems.isEmpty()) {
            return true;  // 신규 변형이 전부 중복으로 스킵됨 — 가격 갱신은 위에서 수행, UPDATED
        }
        productRepository.saveAndFlush(product);  // 새 ProductItem id 확보
        for (int i = 0; i < persisted.size(); i++) {
            saveListing(group.getBaseSourceKey(), product.getId(), addedItems.get(i), persisted.get(i));
        }
        return true;
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

    // ProductItem 옵션값 5개를 조합 키로 (동일 조합 중복 판정용)
    private String optionCombo(ProductItem item) {
        return String.join("", item.getOptionValue1(), item.getOptionValue2(),
            item.getOptionValue3(), item.getOptionValue4(), item.getOptionValue5());
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
        return (axes != null && idx < axes.size()) ? truncate(axes.get(idx)) : null;
    }

    // ProductItem.optionValue는 NOT NULL이라 미사용 축은 빈 문자열로 채운다 (unique 제약 충돌 방지)
    private String optionValueAt(ProductVariant variant, int idx) {
        List<String> values = variant.optionValues();
        if (values == null || idx >= values.size() || values.get(idx) == null) {
            return "";
        }
        return truncate(values.get(idx));
    }

    // 옵션축/값이 컬럼 길이(100)를 넘으면 안전 절단 — 길어도 그룹 전체 INSERT 실패 방지
    // 100번째 char가 서로게이트 페어 앞쪽이면 한 칸 당겨 자른다(이모지 등 보조평면 문자 분열 방지)
    private String truncate(String s) {
        if (s == null || s.length() <= MAX_OPTION_LEN) {
            return s;
        }
        int end = MAX_OPTION_LEN;
        if (Character.isHighSurrogate(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }
}
