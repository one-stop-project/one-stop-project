package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.Category;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductCategoryMapping;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.config.QuerydslConfig;
import com.sparta.one_stop.global.enums.product.ProductItemStatus;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.product.SortType;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.enums.user.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 상품 검색 QueryDSL 동적 쿼리 통합 테스트 (실제 H2 실행)
// FULLTEXT(MATCH AGAINST) 키워드 경로는 H2 미지원이라 여기서 제외하고,
// 실제 MySQL + ngram 기준은 ProductSearchFulltextIntegrationTest(Testcontainers)에서 검증한다.
// 여기서는 키워드 없는 동적 쿼리 로직(정렬/카테고리/가격필터/ON_SALE 의미/페이징)을 검증한다.
// JPA 슬라이스 — @Scheduled 백그라운드 빈을 안 띄워서 전역 통계 카운터가 오염되지 않음
// (N+1 검증의 쿼리 수가 백그라운드 쿼리에 밀려 흔들리던 문제 제거). H2(MODE=MySQL) test 프로필 그대로 사용.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
@DisplayName("ProductRepository.search - QueryDSL 동적 검색 (키워드 제외)")
class ProductRepositorySearchTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SellerRepository sellerRepository;
    @Autowired private CategoryRepository categoryRepository;
    @PersistenceContext private EntityManager em;

    private Seller seller;
    private Category cat1;
    private Category cat2;
    private Product pA;  // cat1, ON_SALE [3000]
    private Product pB;  // cat1, ON_SALE [1000, 5000]
    private Product pC;  // cat2, STOP   [2000]  (ON_SALE 옵션 없음)
    private Product pD;  // cat1, ON_SALE [10000]

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
            .email("seller@test.com").password("pw").name("판매자").role(UserRole.SELLER).build());
        Seller s = Seller.builder()
            .user(user).shopName("테스트샵").businessNumber("1234567890").build();
        s.approve();
        seller = sellerRepository.save(s);

        cat1 = categoryRepository.save(Category.builder().name("카테고리1").build());
        cat2 = categoryRepository.save(Category.builder().name("카테고리2").build());

        pA = persistProduct("상품A", cat1, new long[]{3000}, new long[]{});
        pB = persistProduct("상품B", cat1, new long[]{1000, 5000}, new long[]{});
        pC = persistProduct("상품C", cat2, new long[]{}, new long[]{2000});
        pD = persistProduct("상품D", cat1, new long[]{10000}, new long[]{});

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("LATEST: 승인 상품 전체를 최신순(createdAt desc, id tie-break)으로 (ON_SALE 옵션 없어도 포함)")
    void latest_allApproved_orderByCreatedAtDesc() {
        Page<Product> page = productRepository.search(
            cond(SortType.LATEST, null, null, null), PageRequest.of(0, 10));

        assertThat(ids(page)).containsExactly(pD.getId(), pC.getId(), pB.getId(), pA.getId());
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("PRICE_ASC: ON_SALE 옵션 있는 상품만, ON_SALE 최저가 오름차순 (STOP만 있는 상품 제외)")
    void priceAsc_onSaleOnly_orderByMinPriceAsc() {
        Page<Product> page = productRepository.search(
            cond(SortType.PRICE_ASC, null, null, null), PageRequest.of(0, 10));

        assertThat(ids(page)).containsExactly(pB.getId(), pA.getId(), pD.getId());
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("PRICE_DESC: ON_SALE 최저가 내림차순")
    void priceDesc_orderByMinPriceDesc() {
        Page<Product> page = productRepository.search(
            cond(SortType.PRICE_DESC, null, null, null), PageRequest.of(0, 10));

        assertThat(ids(page)).containsExactly(pD.getId(), pA.getId(), pB.getId());
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("카테고리 필터: 해당 카테고리 매핑 상품만 반환")
    void categoryFilter_returnsOnlyMatching() {
        Page<Product> page = productRepository.search(
            cond(SortType.LATEST, cat1.getId(), null, null), PageRequest.of(0, 10));

        assertThat(ids(page)).containsExactly(pD.getId(), pB.getId(), pA.getId());
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("LATEST 가격필터: 상태 무관 어떤 옵션이든 범위에 들면 노출 (STOP 옵션 포함)")
    void latestPriceFilter_anyItemInRange() {
        // [2000,4000]: 상품A(ON_SALE 3000), 상품C(STOP 2000) 포함 / 상품B(1000·5000)·상품D(10000) 제외
        Page<Product> page = productRepository.search(
            cond(SortType.LATEST, null, 2000L, 4000L), PageRequest.of(0, 10));

        assertThat(ids(page)).containsExactly(pC.getId(), pA.getId());
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("PRICE 정렬 가격필터: ON_SALE 옵션이 범위에 든 상품만 (STOP·범위밖 제외)")
    void priceSortPriceFilter_onSaleInRangeOnly() {
        // [2000,4000]: 상품A(ON_SALE 3000)만. 상품C(STOP 2000)·상품B(ON_SALE지만 범위밖) 제외
        Page<Product> page = productRepository.search(
            cond(SortType.PRICE_ASC, null, 2000L, 4000L), PageRequest.of(0, 10));

        assertThat(ids(page)).containsExactly(pA.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("페이징: 첫 페이지 size=2 → 2건 반환, total은 전체 건수")
    void pagination_returnsPageSlice_withFullTotal() {
        Page<Product> page = productRepository.search(
            cond(SortType.LATEST, null, null, null), PageRequest.of(0, 2));

        assertThat(ids(page)).containsExactly(pD.getId(), pC.getId());
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("페이징: 2페이지(page=1) → 다음 슬라이스(B,A) 반환")
    void pagination_secondPage_returnsNextSlice() {
        // 전체 LATEST = [D, C, B, A], size 2 → page 1 = [B, A]
        Page<Product> page = productRepository.search(
            cond(SortType.LATEST, null, null, null), PageRequest.of(1, 2));

        assertThat(ids(page)).containsExactly(pB.getId(), pA.getId());
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("연관상품(findRelated): 같은 카테고리 + 판매중(ON_SALE)·재고 보유 + 자기 제외, 인기점수(조회70+판매30) 내림차순")
    void findRelated_sameCategory_onSaleOnly_excludeSelf_orderByPopularity() {
        // 인기점수 차등: pD(조회수 100 → 0.7*100=70) > pB(판매수 100 → 0.3*100=30)
        bumpScore(pD, 100, 0);
        bumpScore(pB, 0, 100);
        em.flush();
        em.clear();

        List<Product> result = productRepository.findRelated(
            List.of(cat1.getId()), pA.getId(),
            ProductStatus.APPROVED, SellerStatus.APPROVED, ProductItemStatus.ON_SALE,
            PageRequest.of(0, 10));

        // cat1 + ON_SALE + 자기(pA) 제외 → pB, pD만 (pC는 cat2·STOP이라 제외). 점수순 pD > pB.
        assertThat(result.stream().map(Product::getId).toList())
            .containsExactly(pD.getId(), pB.getId());
    }

    @Test
    @DisplayName("연관상품(findRelated): 같은 카테고리여도 재고 0(ON_SALE)·STOP 옵션은 제외")
    void findRelated_excludesOutOfStockAndStopped_inSameCategory() {
        // cat1 + ON_SALE이지만 재고 0 → i.stock > 0 필터로 제외돼야 함
        Product outOfStock = persistSingleItemProduct("재고0", cat1, ProductItemStatus.ON_SALE, 0L);
        // cat1 + 재고 있지만 STOP → i.status = ON_SALE 필터로 제외 (카테고리 안에서도 빠지는지)
        Product stopped = persistSingleItemProduct("정지", cat1, ProductItemStatus.STOP, 10L);
        em.flush();
        em.clear();

        List<Product> result = productRepository.findRelated(
            List.of(cat1.getId()), pA.getId(),
            ProductStatus.APPROVED, SellerStatus.APPROVED, ProductItemStatus.ON_SALE,
            PageRequest.of(0, 10));

        List<Long> resultIds = result.stream().map(Product::getId).toList();
        // 정상(cat1·판매중·재고>0)인 pB, pD만 남고, 재고0·STOP은 제외
        assertThat(resultIds).containsExactlyInAnyOrder(pB.getId(), pD.getId());
        assertThat(resultIds).doesNotContain(outOfStock.getId(), stopped.getId());
    }

    // ===== 헬퍼 =====

    private void bumpScore(Product p, long views, long sales) {
        Product managed = em.find(Product.class, p.getId());
        if (views > 0) managed.syncViewCount(views);
        if (sales > 0) managed.increaseSalesCount(sales);
    }

    // 옵션 1건짜리 상품(상태·재고 지정) — 연관 필터(판매중·재고>0) 검증용
    private Product persistSingleItemProduct(String name, Category category, ProductItemStatus status, long stock) {
        Product p = Product.builder()
            .seller(seller).name(name).description(name + " 설명").thumbnailUrl("http://img/" + name)
            .build();
        p.approve();
        ProductItem it = ProductItem.builder()
            .product(p)
            .optionValue1("opt").optionValue2("기본").optionValue3("기본")
            .optionValue4("기본").optionValue5("기본")
            .price(3000L).stock(stock)                 // 생성자 기본 상태 = ON_SALE
            .build();
        if (status == ProductItemStatus.STOP) {
            it.stop();
        }
        p.getProductItems().add(it);
        p.getCategoryMappings().add(
            ProductCategoryMapping.builder().product(p).category(category).build());
        return productRepository.save(p);
    }

    @Test
    @DisplayName("검색 목록은 옵션을 묶어 가져온다 — 상품 수만큼 쿼리가 늘지 않음 (N+1 없음)")
    void search_batchesProductItems_noNPlusOne() {
        Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        em.flush();
        em.clear();

        // 승인 상품 4건(각 옵션 보유)을 검색으로 로드 — 이 시점엔 옵션 미로딩
        Page<Product> page = productRepository.search(
            cond(SortType.LATEST, null, null, null), PageRequest.of(0, 10));

        // 검색 쿼리는 빼고, 옵션 접근으로 발생하는 쿼리만 센다
        stats.clear();
        page.getContent().forEach(p -> p.getProductItems().size());

        // @BatchSize로 모든 상품 옵션이 한 번에 묶여 1쿼리 없으면 상품 수만큼 발생
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    private ProductSearchCond cond(SortType sort, Long categoryId, Long minPrice, Long maxPrice) {
        return new ProductSearchCond(
            ProductStatus.APPROVED, SellerStatus.APPROVED, null, categoryId, minPrice, maxPrice, sort, null);
    }

    private List<Long> ids(Page<Product> page) {
        return page.getContent().stream().map(Product::getId).toList();
    }

    private Product persistProduct(String name, Category category, long[] onSalePrices, long[] stoppedPrices) {
        Product p = Product.builder()
            .seller(seller).name(name).description(name + " 설명").thumbnailUrl("http://img/" + name)
            .build();
        p.approve();
        for (long price : onSalePrices) {
            p.getProductItems().add(item(p, price));            // 생성자 기본값 ON_SALE
        }
        for (long price : stoppedPrices) {
            ProductItem stopped = item(p, price);
            stopped.stop();                                     // STOP
            p.getProductItems().add(stopped);
        }
        p.getCategoryMappings().add(
            ProductCategoryMapping.builder().product(p).category(category).build());
        return productRepository.save(p);
    }

    private ProductItem item(Product product, long price) {
        // 동일 상품 내 옵션 조합 유니크 제약 충족 — 가격으로 조합을 구분(상품 내 가격은 서로 다름)
        return ProductItem.builder()
            .product(product)
            .optionValue1("opt-" + price).optionValue2("기본").optionValue3("기본")
            .optionValue4("기본").optionValue5("기본")
            .price(price).stock(10L)
            .build();
    }
}
