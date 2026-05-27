package com.sparta.one_stop.domain.product.service;

import com.sparta.one_stop.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 누적 카운트를 DB에 반영 (상품별 개별 트랜잭션)
// Redis acknowledge는 호출자(Scheduler)가 트랜잭션 커밋 성공 확인 후 수행
@Service
@RequiredArgsConstructor
public class ProductViewCountSyncService {

    private final ProductRepository productRepository;

    @Transactional
    public void syncOne(Long productId, long count) {
        if (count <= 0) {
            return;
        }
        productRepository.findById(productId)
            .ifPresent(product -> product.syncViewCount(count));
    }

    @Transactional
    public int resetAllViewCounts() {
        return productRepository.resetAllViewCounts();
    }
}
