package com.sparta.one_stop.domain.ai.repository;

import com.sparta.one_stop.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiReviewReader extends JpaRepository<Review, Long> {

    List<Review> findAllByProduct_Id(Long productId);

    long countByProduct_Id(Long productId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId")
    Double findAverageRatingByProductId(@Param("productId") Long productId);
}
