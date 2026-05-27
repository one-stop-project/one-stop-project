package com.sparta.one_stop.domain.product.service;

import com.sparta.one_stop.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Redis에 누적된 조회수를 DB에 동기화 (상품별 개별 트랜잭션)
@Service
@RequiredArgsConstructor
public class ProductViewCountSyncService {

    private final ProductViewCountService viewCountService;
    private final ProductRepository productRepository;

    @Transactional
    public void syncOne(Long productId) {
        long count = viewCountService.flushAndGetCount(productId);
        if (count <= 0) {
            return;
        }
        productRepository.findById(productId)
            .ifPresent(product -> product.syncViewCount(count));
    }
}
