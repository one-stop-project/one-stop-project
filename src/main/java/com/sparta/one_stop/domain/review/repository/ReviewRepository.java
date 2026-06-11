package com.sparta.one_stop.domain.review.repository;

import com.sparta.one_stop.domain.review.entity.Review;
import com.sparta.one_stop.global.enums.review.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderItem_Id(Long orderItemId);

    Optional<Review> findByIdAndUser_IdAndStatus(Long reviewId, Long userId, ReviewStatus status);

    // 내 리뷰 목록: ACTIVE만 조회
    Page<Review> findAllByUser_IdAndStatus(Long userId, ReviewStatus status, Pageable pageable);

    // 리뷰 작성 가능 목록 조회 시 사용
    // existsByOrderItem_Id() 반복 호출에 따른 N+1 문제 방지
    // 재작성 불가: 삭제된 리뷰도 "이미 작성됨"으로 간주
    @Query("""
    select r.orderItem.id
    from Review r
    where r.orderItem.id in :orderItemIds
    """)
    List<Long> findReviewedOrderItemIds(
        @Param("orderItemIds") List<Long> orderItemIds
    );

    // ── AI 요약 전용 ─────────────────────────────────────────────

    // 최신순 최대 N건 — 전체 요약(최초/강제 갱신) 시 사용
    List<Review> findAllByProduct_IdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    long countByProduct_Id(Long productId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId")
    Double findAverageRatingByProductId(@Param("productId") Long productId);

    // 증분 업데이트 — lastIncludedId 초과 ~ newReviewId 이하 범위, 오래된 순
    // ID 범위 커서와 정렬 기준을 id ASC로 통일 — createdAt 기준 정렬 시 max(id) 계산이 어긋날 수 있음
    @Query("SELECT r FROM Review r WHERE r.product.id = :productId AND r.id > :afterId AND r.id <= :upToId ORDER BY r.id ASC")
    List<Review> findNewReviewsBetween(@Param("productId") Long productId,
                                       @Param("afterId") Long afterId,
                                       @Param("upToId") Long upToId);
}
