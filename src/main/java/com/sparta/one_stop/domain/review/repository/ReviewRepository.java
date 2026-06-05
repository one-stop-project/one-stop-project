package com.sparta.one_stop.domain.review.repository;

import com.sparta.one_stop.domain.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderItem_Id(Long orderItemId);

    Optional<Review> findByIdAndUser_Id(Long reviewId, Long userId);

    Page<Review> findAllByUser_Id(Long userId, Pageable pageable);

    // ── AI 요약 전용 ─────────────────────────────────────────────

    // 최신순 최대 N건 — 전체 요약(최초/강제 갱신) 시 사용
    List<Review> findAllByProduct_IdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    long countByProduct_Id(Long productId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId")
    Double findAverageRatingByProductId(@Param("productId") Long productId);

    // 증분 업데이트 — lastIncludedId 초과 ~ newReviewId 이하 범위, 오래된 순
    @Query("SELECT r FROM Review r WHERE r.product.id = :productId AND r.id > :afterId AND r.id <= :upToId ORDER BY r.createdAt ASC")
    List<Review> findNewReviewsBetween(@Param("productId") Long productId,
                                       @Param("afterId") Long afterId,
                                       @Param("upToId") Long upToId);
}
