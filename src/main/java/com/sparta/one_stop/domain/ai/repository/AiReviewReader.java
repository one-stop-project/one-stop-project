package com.sparta.one_stop.domain.ai.repository;

import com.sparta.one_stop.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @deprecated AI 전용 쿼리가 {@link com.sparta.one_stop.domain.review.repository.ReviewRepository}로 통합됨
 */
@Deprecated
public interface AiReviewReader extends JpaRepository<Review, Long> {
}
