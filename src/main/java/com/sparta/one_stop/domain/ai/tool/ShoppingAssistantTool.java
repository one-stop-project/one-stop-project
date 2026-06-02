package com.sparta.one_stop.domain.ai.tool;

import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.product.repository.ProductRepositoryImpl;
import com.sparta.one_stop.domain.product.repository.ProductSearchCond;
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

    @Tool(description = "키워드와 최대 가격으로 판매 중인 상품을 검색합니다. keyword가 없으면 null을 전달하세요. 상품 ID와 이름 목록을 반환합니다.")
    public List<ProductInfo> searchProducts(String keyword, Long maxPrice) {
        ProductSearchCond cond = new ProductSearchCond(
            ProductStatus.APPROVED, SellerStatus.APPROVED,
            sanitize(keyword), null, null, maxPrice, SortType.LATEST
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
