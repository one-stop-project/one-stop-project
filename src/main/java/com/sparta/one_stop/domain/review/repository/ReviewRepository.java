package com.sparta.one_stop.domain.review.repository;

import com.sparta.one_stop.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderItem_Id(Long orderItemId);

    Optional<Review> findByIdAndUser_Id(Long reviewId, Long userId);
}
