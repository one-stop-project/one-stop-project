package com.sparta.one_stop.domain.review.repository;

import com.sparta.one_stop.domain.review.entity.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {
}
