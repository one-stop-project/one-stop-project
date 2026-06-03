package com.sparta.one_stop.domain.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.one_stop.domain.product.entity.Category;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductCategoryMapping;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.product.SortType;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.enums.user.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// 상품 검색 QueryDSL 동적 쿼리 통합 테스트 (실제 H2 실행)
// FULLTEXT(MATCH AGAINST) 키워드 경로는 H2 미지원이라 여기서 제외 — MySQL 수동 검증 대상.
// 여기서는 키워드 없는 동적 쿼리 로직(정렬/카테고리/가격필터/ON_SALE 의미/페이징)을 검증한다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
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

    // ===== 헬퍼 =====

    private ProductSearchCond cond(SortType sort, Long categoryId, Long minPrice, Long maxPrice) {
        return new ProductSearchCond(
            ProductStatus.APPROVED, SellerStatus.APPROVED, null, categoryId, minPrice, maxPrice, sort);
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
        return ProductItem.builder()
            .product(product)
            .optionValue1("기본").optionValue2("기본").optionValue3("기본")
            .optionValue4("기본").optionValue5("기본")
            .price(price).stock(10L)
            .build();
    }
}
