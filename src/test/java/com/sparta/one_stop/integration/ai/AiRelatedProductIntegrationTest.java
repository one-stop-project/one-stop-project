package com.sparta.one_stop.integration.ai;

import com.sparta.one_stop.domain.ai.service.AiRelatedProductService;
import com.sparta.one_stop.domain.product.dto.response.ProductSummaryResponse;
import com.sparta.one_stop.domain.product.entity.Category;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductCategoryMapping;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.CategoryRepository;
import com.sparta.one_stop.domain.product.repository.ProductCategoryMappingRepository;
import com.sparta.one_stop.domain.product.repository.ProductItemRepository;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.integration.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@DisplayName("AI 연관 상품 추천 통합 테스트")
class AiRelatedProductIntegrationTest extends IntegrationTestSupport {

    @Autowired private AiRelatedProductService aiRelatedProductService;
    @Autowired private UserRepository userRepository;
    @Autowired private SellerRepository sellerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductItemRepository productItemRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductCategoryMappingRepository categoryMappingRepository;

    private Seller seller;
    private Category category;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
            .email("related-seller@test.com")
            .password("pass")
            .name("테스트판매자")
            .role(UserRole.SELLER)
            .build());

        seller = sellerRepository.save(Seller.builder()
            .user(user)
            .shopName("테스트상점")
            .businessNumber("111-22-33333")
            .build());
        seller.approve();

        category = categoryRepository.save(Category.builder()
            .name("테스트카테고리")
            .build());
    }

    @Test
    @DisplayName("동일 카테고리 상품이 추천 목록에 포함된다")
    void getRelatedProducts_returns_same_category_products() {
        Product target = approvedProductWithStock("기준상품", 0L);
        Product related1 = approvedProductWithStock("연관상품A", 10L);
        Product related2 = approvedProductWithStock("연관상품B", 5L);
        mapCategory(target);
        mapCategory(related1);
        mapCategory(related2);

        List<ProductSummaryResponse> result = aiRelatedProductService.getRelatedProducts(target.getId());

        List<Long> resultIds = result.stream().map(ProductSummaryResponse::getProductId).toList();
        assertThat(resultIds).contains(related1.getId(), related2.getId());
    }

    @Test
    @DisplayName("자기 자신은 추천 목록에서 제외된다")
    void getRelatedProducts_excludes_self() {
        Product target = approvedProductWithStock("기준상품", 5L);
        mapCategory(target);

        List<ProductSummaryResponse> result = aiRelatedProductService.getRelatedProducts(target.getId());

        List<Long> resultIds = result.stream().map(ProductSummaryResponse::getProductId).toList();
        assertThat(resultIds).doesNotContain(target.getId());
    }

    @Test
    @DisplayName("품절 상품은 추천 목록에서 제외된다")
    void getRelatedProducts_excludes_out_of_stock() {
        Product target = approvedProductWithStock("기준상품", 5L);
        Product outOfStock = approvedProductWithStock("품절상품", 0L);
        mapCategory(target);
        mapCategory(outOfStock);

        List<ProductSummaryResponse> result = aiRelatedProductService.getRelatedProducts(target.getId());

        List<Long> resultIds = result.stream().map(ProductSummaryResponse::getProductId).toList();
        assertThat(resultIds).doesNotContain(outOfStock.getId());
    }

    @Test
    @DisplayName("판매수 높은 순으로 정렬된다")
    void getRelatedProducts_sorted_by_sales_count_desc() {
        Product target = approvedProductWithStock("기준상품", 1L);
        Product low = approvedProductWithStock("판매낮음", 1L);
        Product high = approvedProductWithStock("판매높음", 1L);
        setSalesCount(low, 1L);
        setSalesCount(high, 100L);
        mapCategory(target);
        mapCategory(low);
        mapCategory(high);

        List<ProductSummaryResponse> result = aiRelatedProductService.getRelatedProducts(target.getId());

        List<Long> resultIds = result.stream().map(ProductSummaryResponse::getProductId).toList();
        assertThat(resultIds.indexOf(high.getId())).isLessThan(resultIds.indexOf(low.getId()));
    }

    @Test
    @DisplayName("카테고리 없는 상품은 빈 목록을 반환한다")
    void getRelatedProducts_returns_empty_when_no_category() {
        Product target = approvedProductWithStock("카테고리없는상품", 5L);

        List<ProductSummaryResponse> result = aiRelatedProductService.getRelatedProducts(target.getId());

        assertThat(result).isEmpty();
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private Product approvedProductWithStock(String name, long stock) {
        Product product = productRepository.save(Product.builder()
            .seller(seller)
            .name(name)
            .description("설명")
            .build());
        product.approve();
        productRepository.save(product);

        productItemRepository.save(ProductItem.builder()
            .product(product)
            .optionValue1("기본")
            .optionValue2("-").optionValue3("-").optionValue4("-").optionValue5("-")
            .price(10000L)
            .stock(stock)
            .build());

        return product;
    }

    private void mapCategory(Product product) {
        categoryMappingRepository.save(ProductCategoryMapping.builder()
            .product(product)
            .category(category)
            .build());
    }

    private void setSalesCount(Product product, long count) {
        product.increaseSalesCount(count);
        productRepository.save(product);
    }
}
