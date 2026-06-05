package com.sparta.one_stop.domain.ai.repository;

import com.sparta.one_stop.domain.ai.entity.ProductReviewSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductReviewSummaryRepository extends JpaRepository<ProductReviewSummary, Long> {

    Optional<ProductReviewSummary> findByProduct_Id(Long productId);
}
