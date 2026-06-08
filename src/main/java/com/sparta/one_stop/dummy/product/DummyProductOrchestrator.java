package com.sparta.one_stop.dummy.product;

import com.sparta.one_stop.domain.product.entity.Category;
import com.sparta.one_stop.domain.product.repository.CategoryRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.dummy.DummySeedProperties;
import com.sparta.one_stop.dummy.dedup.ProductDeduplicator;
import com.sparta.one_stop.dummy.grouping.GroupedProduct;
import com.sparta.one_stop.dummy.grouping.NaverVariantGrouper;
import com.sparta.one_stop.dummy.naver.NaverShopClient;
import com.sparta.one_stop.dummy.naver.dto.NaverShopItem;
import com.sparta.one_stop.dummy.seed.CategorySeeder;
import com.sparta.one_stop.dummy.seed.DummySellerSeeder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// 더미 상품 시드 파이프라인 총괄 — 카테고리/판매자 시드 → 소카테고리별 (검색→dedup→변형그룹핑→이미지→저장).
// 외부호출(네이버·LLM·이미지)은 트랜잭션 밖, 저장은 writer가 그룹당 독립 트랜잭션. 카테고리/그룹 단위로 실패 격리.
// 네이버 키(naver.api.client-id) 설정된 환경에서만 활성화 — 키 없는 환경(동료/CI)에선 빈 자체가 안 뜸.
@Component
@ConditionalOnProperty(prefix = "naver.api", name = "client-id")
@RequiredArgsConstructor
public class DummyProductOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DummyProductOrchestrator.class);
    private static final String SORT = "sim";
    private static final int START = 1;
    private static final int MAX_CATEGORY_MAPPING = 3;  // 정책 §13: 상품-카테고리 매핑 1~3개

    private final DummySellerSeeder sellerSeeder;
    private final CategorySeeder categorySeeder;
    private final CategoryRepository categoryRepository;
    private final NaverShopClient naverShopClient;
    private final ProductDeduplicator deduplicator;
    private final NaverVariantGrouper grouper;
    private final DummyImageFetcher imageFetcher;
    private final DummyProductWriter writer;
    private final DummySeedProperties properties;

    public void run() {
        log.info("[더미시드] 시작");
        categorySeeder.seed();
        Seller seller = sellerSeeder.seed();

        List<Category> leaves = leafCategories();
        log.info("[더미시드] 소카테고리 {}개 대상 (display={}, throttle={}ms)",
                leaves.size(), properties.display(), properties.throttleMs());

        int created = 0;
        int updated = 0;
        int failed = 0;
        int categoriesDone = 0;

        for (Category leaf : leaves) {
            try {
                List<Long> categoryIds = categoryChain(leaf);
                List<NaverShopItem> items = naverShopClient.search(leaf.getName(), properties.display(), START, SORT);
                List<NaverShopItem> deduped = deduplicator.dedup(items);
                if (deduped.isEmpty()) {
                    continue;
                }
                List<GroupedProduct> groups = grouper.group(deduped, categoryIds, leaf.getName());
                for (GroupedProduct group : groups) {
                    try {
                        String thumbnail = imageFetcher.fetchOrDefault(group.thumbnailImageUrl());
                        DummyWriteResult result = writer.write(seller, withThumbnail(group, thumbnail));
                        if (result == DummyWriteResult.CREATED) {
                            created++;
                        } else {
                            updated++;
                        }
                    } catch (Exception e) {
                        failed++;
                        log.warn("[더미시드] 상품 저장 실패 (category={}, base={})",
                                leaf.getName(), group.baseSourceKey(), e);
                    }
                }
            } catch (Exception e) {
                log.warn("[더미시드] 카테고리 처리 실패 (category={})", leaf.getName(), e);
            } finally {
                categoriesDone++;
                throttle();
            }
        }

        log.info("[더미시드] 완료 — 카테고리 {}/{}, 생성 {}, 갱신 {}, 실패 {}",
                categoriesDone, leaves.size(), created, updated, failed);
    }

    // 잎(소) 카테고리 = 다른 카테고리의 부모가 아닌 노드
    private List<Category> leafCategories() {
        List<Category> all = categoryRepository.findAllWithAncestors();
        Set<Long> parentIds = all.stream()
                .map(Category::getParent)
                .filter(Objects::nonNull)
                .map(Category::getId)
                .collect(Collectors.toSet());
        return all.stream()
                .filter(category -> !parentIds.contains(category.getId()))
                .toList();
    }

    // 잎 → 상위로 올라가며 최대 3개 (정책 §13)
    private List<Long> categoryChain(Category leaf) {
        List<Long> ids = new ArrayList<>();
        Category current = leaf;
        while (current != null && ids.size() < MAX_CATEGORY_MAPPING) {
            ids.add(current.getId());
            current = current.getParent();
        }
        return ids;
    }

    private GroupedProduct withThumbnail(GroupedProduct group, String thumbnailUrl) {
        return new GroupedProduct(
                group.baseSourceKey(), group.name(), group.description(),
                group.categoryIds(), group.optionAxisNames(), thumbnailUrl, group.variants());
    }

    // LLM 분당 한도(RPM) 보호 — 그룹퍼가 카테고리당 1콜이므로 카테고리 사이에 간격을 둔다
    private void throttle() {
        long ms = properties.throttleMs();
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
