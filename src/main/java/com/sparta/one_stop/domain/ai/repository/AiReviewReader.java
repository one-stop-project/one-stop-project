package com.sparta.one_stop.domain.ai.repository;

import com.sparta.one_stop.domain.review.entity.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiReviewReader extends JpaRepository<Review, Long> {

    // 최신순 최대 N건만 조회 — 토큰 비용 및 컨텍스트 윈도우 초과 방지
    List<Review> findAllByProduct_IdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    long countByProduct_Id(Long productId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId")
    Double findAverageRatingByProductId(@Param("productId") Long productId);
}
