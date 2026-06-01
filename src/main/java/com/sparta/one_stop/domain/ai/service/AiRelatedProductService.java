package com.sparta.one_stop.domain.ai.service;

import com.sparta.one_stop.domain.ai.dto.RelatedProductResponse;
import com.sparta.one_stop.domain.ai.repository.AiProductReader;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.product.service.PopularProductService;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiRelatedProductService {

    private static final int RELATED_LIMIT = 10;
    private static final int CANDIDATE_FETCH_SIZE = 30; // 품절 필터 후 10개 확보용

    private final AiProductReader aiProductReader;
    private final ProductRepository productRepository;
    private final PopularProductService popularProductService;

    @CircuitBreaker(name = "ai-assistant", fallbackMethod = "getRelatedProductsFallback")
    public List<RelatedProductResponse> getRelatedProducts(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        List<Long> categoryIds = product.getCategoryMappings().stream()
            .map(m -> m.getCategory().getId())
            .toList();

        if (categoryIds.isEmpty()) {
            return getPopularFallback(productId);
        }

        List<Long> candidateIds = aiProductReader.findIdsByCategoryIds(
            categoryIds, productId, ProductStatus.APPROVED,
            PageRequest.of(0, CANDIDATE_FETCH_SIZE)
        );

        if (candidateIds.isEmpty()) {
            return getPopularFallback(productId);
        }

        return aiProductReader.findWithItemsByIds(candidateIds).stream()
            .filter(this::hasStock)
            .sorted((a, b) -> Long.compare(b.getSalesCount(), a.getSalesCount()))
            .limit(RELATED_LIMIT)
            .map(RelatedProductResponse::from)
            .toList();
    }

    // Circuit Breaker fallback — Redis ZSET 인기상품 반환
    List<RelatedProductResponse> getRelatedProductsFallback(Long productId, Throwable t) {
        log.warn("[AI Related] circuit breaker triggered. productId={} reason={}", productId, t.getMessage());
        return getPopularFallback(productId);
    }

    private List<RelatedProductResponse> getPopularFallback(Long productId) {
        List<Long> popularIds = popularProductService.getPopularProductIds(RELATED_LIMIT * 2);
        if (popularIds.isEmpty()) {
            return List.of();
        }
        return aiProductReader.findWithItemsByIds(popularIds).stream()
            .filter(p -> !p.getId().equals(productId))
            .filter(this::hasStock)
            .limit(RELATED_LIMIT)
            .map(RelatedProductResponse::from)
            .toList();
    }

    private boolean hasStock(Product product) {
        return product.isApproved()
            && product.getProductItems().stream().anyMatch(item -> item.getStock() > 0);
    }
}
