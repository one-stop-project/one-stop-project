package com.sparta.one_stop.domain.ai.tool;

import com.sparta.one_stop.domain.ai.tool.ShoppingAssistantTool.ProductInfo;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.ai.logging.AiTokenLogger;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShoppingAssistantTool - 태그 검색 응답 정렬")
class ShoppingAssistantToolTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AiTokenLogger tokenLogger;

    @InjectMocks
    private ShoppingAssistantTool tool;

    @Test
    @DisplayName("searchProducts 결과는 상품명 오름차순(가나다)으로 정렬되어 반환된다")
    void searchProducts_resultSortedByName() {
        // given — 정렬되지 않은 순서로 반환되도록 mock
        given(productRepository.search(any(), any()))
            .willReturn(new PageImpl<>(List.of(
                product(3L, "다 상품"),
                product(1L, "가 상품"),
                product(2L, "나 상품"))));

        // when
        List<ProductInfo> result = tool.searchProducts("키워드", null, null, null);

        // then — 이름 오름차순으로 응답
        assertThat(result).extracting(ProductInfo::name)
            .containsExactly("가 상품", "나 상품", "다 상품");
    }

    private Product product(Long id, String name) {
        Seller seller = Seller.builder()
            .shopName("shop").businessNumber("1234567890").build();
        Product p = Product.builder()
            .seller(seller).name(name).build();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }
}
