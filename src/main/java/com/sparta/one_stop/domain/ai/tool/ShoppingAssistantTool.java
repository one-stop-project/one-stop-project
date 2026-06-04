package com.sparta.one_stop.domain.ai.tool;

import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.product.repository.ProductSearchCond;
import com.sparta.one_stop.global.ai.logging.AiTokenLogger;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.product.SortType;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShoppingAssistantTool {

    private static final int SEARCH_LIMIT = 5;

    private final ProductRepository productRepository;
    private final AiTokenLogger tokenLogger;

    @Tool(description = """
        판매 중인 상품을 검색합니다. 없는 파라미터는 null로 전달하세요.
        - keyword: 상품명/설명과 관련된 핵심 키워드
        - tags: 상품에 등록된 태그 목록 (예: ["방수", "경량"]). 지정한 태그 중 하나라도 가진 상품이 반환됩니다(OR 매칭).
        - categoryId: 검색할 카테고리 ID. 시스템이 힌트를 제공한 경우 관련 질문이면 사용하고, 아니면 null로 전체 검색하세요.
        - maxPrice: 최대 가격 조건
        """)
    public List<ProductInfo> searchProducts(String keyword, List<String> tags, Long categoryId, Long maxPrice) {
        tokenLogger.logSearchCall(keyword, tags, categoryId, maxPrice);
        ProductSearchCond cond = new ProductSearchCond(
            ProductStatus.APPROVED, SellerStatus.APPROVED,
            sanitize(keyword), categoryId, null, maxPrice, SortType.LATEST,
            (tags != null && !tags.isEmpty()) ? tags : null
        );
        return productRepository.search(cond, PageRequest.of(0, SEARCH_LIMIT))
            .stream()
            .map(p -> new ProductInfo(p.getId(), p.getName()))
            .toList();
    }

    @Tool(description = "상품 ID로 재고 여부를 확인합니다. hasStock이 true이면 구매 가능합니다.")
    public StockInfo checkStock(Long productId) {
        return productRepository.findWithCollectionsById(productId)
            .map(p -> {
                long total = p.getProductItems().stream()
                    .mapToLong(item -> item.getStock())
                    .sum();
                return new StockInfo(productId, p.getName(), total > 0, total);
            })
            .orElse(new StockInfo(productId, "알 수 없음", false, 0L));
    }

    private String sanitize(String keyword) {
        if (keyword == null) return null;
        String cleaned = keyword.replaceAll("[+\\-~*\"()<>@]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    public record ProductInfo(Long productId, String name) {}

    public record StockInfo(Long productId, String name, boolean hasStock, long totalStock) {}
}
