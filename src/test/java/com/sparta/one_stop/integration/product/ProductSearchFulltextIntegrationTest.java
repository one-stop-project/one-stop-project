package com.sparta.one_stop.integration.product;

import com.sparta.one_stop.domain.product.entity.Category;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductCategoryMapping;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.CategoryRepository;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.product.repository.ProductSearchCond;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.product.SortType;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.integration.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상품 검색 FULLTEXT(MATCH AGAINST) 키워드 통합 테스트 — 실제 MySQL + ngram 파서.
 *
 * 배경:
 * - {@link com.sparta.one_stop.domain.product.repository.ProductRepositorySearchTest}는 H2(@DataJpaTest)라
 *   FULLTEXT 키워드 경로를 돌릴 수 없어 제외하고 "MySQL 수동 검증 대상"으로 남겨 두었다.
 * - 이 테스트가 그 키워드 경로(fulltext_match 커스텀 함수 → MATCH(name, description) AGAINST(? IN BOOLEAN MODE))를
 *   Testcontainers MySQL + ngram FULLTEXT 인덱스로 자동 검증한다.
 *
 * 트랜잭션 정책:
 * - 베이스({@link IntegrationTestSupport})는 @Transactional(롤백)이지만 이 테스트는 NOT_SUPPORTED로 무력화한다.
 *   FULLTEXT 인덱스 생성은 DDL이라 트랜잭션 내 암묵 커밋을 일으키고, InnoDB FULLTEXT 검색은 커밋된 데이터를 대상으로 하기 때문이다.
 *   따라서 데이터는 커밋하고 @AfterEach에서 직접 정리한다.
 *
 * 인덱스 정책:
 * - 운영 인덱스 생성기({@link com.sparta.one_stop.domain.product.support.FulltextIndexInitializer})는 @Profile("!test")라
 *   test 프로필에선 동작하지 않는다. 그래서 이 테스트가 운영과 동일한 인덱스(idx_product_name_fulltext, WITH PARSER ngram)를
 *   기동 시 직접 보장한다(없으면 생성).
 */
@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("상품 검색 FULLTEXT 키워드 - 실제 MySQL + ngram")
class ProductSearchFulltextIntegrationTest extends IntegrationTestSupport {

    private static final String FULLTEXT_INDEX_NAME = "idx_product_name_fulltext";

    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SellerRepository sellerRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Seller approvedSeller;

    @BeforeEach
    void setUp() {
        ensureFulltextIndex();

        approvedSeller = persistSeller("seller@test.com", "테스트샵", "1112223330", SellerStatus.APPROVED);

        // 키워드 매칭 검증용 — name/description에 서로 구분되는 한글 토큰을 둔다(ngram 토큰 크기 2 이상).
        persistProduct(approvedSeller, "사무용 의자", "편안한 사무용 의자입니다", ProductStatus.APPROVED);
        persistProduct(approvedSeller, "게이밍 의자", "장시간 앉아도 편한 게이밍 의자", ProductStatus.APPROVED);
        persistProduct(approvedSeller, "원목 책상", "튼튼한 원목 책상", ProductStatus.APPROVED);
        // 키워드("알루미늄")가 name이 아니라 description에만 있는 상품 — description 컬럼도 인덱스 대상인지 검증
        persistProduct(approvedSeller, "노트북 거치대", "알루미늄 소재 거치대", ProductStatus.APPROVED);
    }

    @AfterEach
    void tearDown() {
        // NOT_SUPPORTED라 커밋된 데이터를 직접 정리한다. product 삭제 시 옵션/카테고리 매핑은 cascade로 함께 제거된다.
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        sellerRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("키워드가 상품명에 있으면 매칭된다 — '의자' 검색은 의자 상품만 반환하고 책상/거치대는 제외한다")
    void keywordInName_matchesByFulltext() {
        Page<Product> page = productRepository.search(
            keywordCond("의자"), PageRequest.of(0, 10));

        assertThat(names(page)).containsExactlyInAnyOrder("사무용 의자", "게이밍 의자");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("키워드가 상품 설명(description)에만 있어도 매칭된다 — name·description 모두 인덱스 대상")
    void keywordInDescriptionOnly_matchesByFulltext() {
        // '알루미늄'은 "노트북 거치대"의 description에만 존재한다.
        Page<Product> page = productRepository.search(
            keywordCond("알루미늄"), PageRequest.of(0, 10));

        assertThat(names(page)).containsExactly("노트북 거치대");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("매칭되는 상품이 없는 키워드는 빈 결과를 반환한다")
    void keywordWithNoMatch_returnsEmpty() {
        Page<Product> page = productRepository.search(
            keywordCond("냉장고"), PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("BOOLEAN MODE 기본 연산자는 OR — '의자 책상'은 의자 또는 책상을 가진 상품을 모두 반환한다")
    void multipleTerms_defaultBooleanModeIsOr() {
        Page<Product> page = productRepository.search(
            keywordCond("의자 책상"), PageRequest.of(0, 10));

        assertThat(names(page)).containsExactlyInAnyOrder("사무용 의자", "게이밍 의자", "원목 책상");
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("승인되지 않은 상품은 키워드가 매칭돼도 제외된다")
    void notApprovedProduct_excludedEvenIfKeywordMatches() {
        // 키워드는 매칭되지만 status가 APPROVE_REQUESTED(미승인)인 상품
        persistProduct(approvedSeller, "접이식 의자", "미승인 접이식 의자", ProductStatus.APPROVE_REQUESTED);

        Page<Product> page = productRepository.search(
            keywordCond("의자"), PageRequest.of(0, 10));

        // 승인된 의자 2건만 — 미승인 "접이식 의자"는 빠진다
        assertThat(names(page)).containsExactlyInAnyOrder("사무용 의자", "게이밍 의자");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("승인되지 않은 판매자의 상품은 키워드가 매칭돼도 제외된다")
    void productOfNotApprovedSeller_excludedEvenIfKeywordMatches() {
        // 판매자는 PENDING이지만 상품 자체는 APPROVED + 키워드 매칭
        Seller pendingSeller = persistSeller("pending@test.com", "대기샵", "9998887770", SellerStatus.PENDING);
        persistProduct(pendingSeller, "리클라이너 의자", "미승인 판매자의 의자", ProductStatus.APPROVED);

        Page<Product> page = productRepository.search(
            keywordCond("의자"), PageRequest.of(0, 10));

        assertThat(names(page)).containsExactlyInAnyOrder("사무용 의자", "게이밍 의자");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("키워드 + 카테고리 필터는 AND로 결합된다 — 키워드 매칭 중 해당 카테고리 상품만 반환")
    void keywordAndCategory_combinedWithAnd() {
        Category chairCategory = categoryRepository.save(Category.builder().name("의자카테고리").build());
        Category etcCategory = categoryRepository.save(Category.builder().name("기타카테고리").build());
        // 둘 다 '의자' 키워드는 매칭하지만 카테고리가 서로 다르다
        persistProductInCategory(approvedSeller, "사무 의자", "사무용 의자", chairCategory);
        persistProductInCategory(approvedSeller, "캠핑 의자", "야외용 캠핑 의자", etcCategory);

        ProductSearchCond cond = new ProductSearchCond(
            ProductStatus.APPROVED, SellerStatus.APPROVED,
            "의자", chairCategory.getId(), null, null, SortType.LATEST, null);
        Page<Product> page = productRepository.search(cond, PageRequest.of(0, 10));

        // '의자' 매칭이면서 chairCategory에 속한 "사무 의자"만 남는다.
        // setUp 의자들(카테고리 매핑 없음)·"캠핑 의자"(etcCategory)는 카테고리 AND 조건에서 제외된다.
        assertThat(names(page)).containsExactly("사무 의자");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("키워드 매칭 결과 페이징 — total은 전체가 아니라 키워드 매칭 건수만 반영한다")
    void keywordResultPaging_totalReflectsKeywordMatchOnly() {
        // setUp의 '의자' 매칭 2건 + 1건 추가 = 매칭 3건 (전체 상품은 5건)
        persistProduct(approvedSeller, "접이 의자", "휴대용 접이 의자", ProductStatus.APPROVED);

        Page<Product> firstPage = productRepository.search(
            keywordCond("의자"), PageRequest.of(0, 2));

        // count 쿼리도 키워드 필터를 타므로 total은 전체(5)가 아니라 매칭(3)이어야 한다.
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("ngram 부분 토큰 매칭 — 공백 없이 붙은 상품명도 중간 토큰으로 검색된다 (ngram 파서 동작 검증)")
    void ngramPartialToken_matchesSubstringInName() {
        // 공백 없는 합성어. ngram(2-gram)이면 '등산' 부분 토큰으로 매칭되지만,
        // 일반 FULLTEXT 파서는 전체를 한 토큰으로 봐 매칭하지 못한다 → ngram이 실제로 동작한다는 직접 증거.
        persistProduct(approvedSeller, "초경량등산스틱", "휴대용 스틱", ProductStatus.APPROVED);

        Page<Product> page = productRepository.search(
            keywordCond("등산"), PageRequest.of(0, 10));

        assertThat(names(page)).containsExactly("초경량등산스틱");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("ngram 토큰 크기(2) 미만 — 1글자 키워드는 토큰화되지 않아 결과가 없다")
    void singleCharKeyword_belowNgramTokenSize_returnsEmpty() {
        // ngram_token_size=2라 1글자 검색어는 인덱스 토큰이 되지 못해 BOOLEAN MODE 매칭이 0건이다.
        Page<Product> page = productRepository.search(
            keywordCond("의"), PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    // ===== 헬퍼 =====

    // test 프로필에선 FulltextIndexInitializer(@Profile("!test"))가 안 도므로, 운영과 동일한 인덱스를 직접 보장한다.
    // MySQL은 FULLTEXT IF NOT EXISTS를 지원하지 않아 존재 여부를 먼저 확인한 뒤 없을 때만 생성한다.
    private void ensureFulltextIndex() {
        Integer cnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'product' AND index_name = ?",
            Integer.class, FULLTEXT_INDEX_NAME);
        if (cnt == null || cnt == 0) {
            jdbcTemplate.execute(
                "ALTER TABLE product ADD FULLTEXT INDEX " + FULLTEXT_INDEX_NAME
                    + " (name, description) WITH PARSER ngram");
        }
    }

    private ProductSearchCond keywordCond(String keyword) {
        return new ProductSearchCond(
            ProductStatus.APPROVED, SellerStatus.APPROVED,
            keyword, null, null, null, SortType.LATEST, null);
    }

    private List<String> names(Page<Product> page) {
        return page.getContent().stream().map(Product::getName).toList();
    }

    private Seller persistSeller(String email, String shopName, String businessNumber, SellerStatus status) {
        User user = userRepository.save(User.builder()
            .email(email)
            .password("password1!")
            .name(shopName + " 사장")
            .phone("010-0000-0000")
            .address("서울시 강남구")
            .role(UserRole.SELLER)
            .build());

        Seller seller = Seller.builder()
            .user(user)
            .shopName(shopName)
            .businessNumber(businessNumber)
            .bankAccount("110-123-456789")
            .build();
        if (status == SellerStatus.APPROVED) {
            seller.approve();
        }
        return sellerRepository.save(seller);
    }

    private Product persistProduct(Seller seller, String name, String description, ProductStatus status) {
        Product product = Product.builder()
            .seller(seller)
            .name(name)
            .description(description)
            .thumbnailUrl("http://img/" + name)
            .build();
        if (status == ProductStatus.APPROVED) {
            product.approve();
        }
        // 검색 노출은 ON_SALE 옵션이 1개 이상 있어야 하므로(#406) 판매중 옵션을 붙인다
        product.getProductItems().add(onSaleItem(product));
        return productRepository.save(product);
    }

    // 카테고리 매핑까지 가진 승인 상품 — 키워드 + 카테고리 조합 검증용
    private Product persistProductInCategory(Seller seller, String name, String description, Category category) {
        Product product = Product.builder()
            .seller(seller)
            .name(name)
            .description(description)
            .thumbnailUrl("http://img/" + name)
            .build();
        product.approve();
        product.getProductItems().add(onSaleItem(product));
        product.getCategoryMappings().add(
            ProductCategoryMapping.builder().product(product).category(category).build());
        return productRepository.save(product);
    }

    // 판매중(ON_SALE) 옵션 1건 — 가격필터 검증이 없는 키워드 테스트라 가격은 고정값 사용
    private ProductItem onSaleItem(Product product) {
        return ProductItem.builder()
            .product(product)
            .optionValue1("기본").optionValue2("기본").optionValue3("기본")
            .optionValue4("기본").optionValue5("기본")
            .price(10000L).stock(10L)              // 생성자 기본 상태 = ON_SALE
            .build();
    }
}
