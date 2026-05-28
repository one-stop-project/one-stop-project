package com.sparta.one_stop.domain.review.repository;

import com.sparta.one_stop.domain.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderItem_Id(Long orderItemId);

    Optional<Review> findByIdAndUser_Id(Long reviewId, Long userId);

    // 리뷰 도메인 - 마이페이지 내 리뷰 목록 조회용
    // 로그인한 사용자가 작성한 리뷰를 페이징 조회
    Page<Review> findAllByUser_Id(Long userId, Pageable pageable);
}
