package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.enums.product.ProductItemStatus;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

    // 상태별 상품 목록 조회 (관리자 승인/반려 목록용)
    List<Product> findAllByStatus(ProductStatus status);

    // 상태별 상품 목록 조회 - 페이징 (관리자 승인 대기 목록용)
    Page<Product> findAllByStatus(ProductStatus status, Pageable pageable);

    // 판매자 ID로 상품 목록 조회
    List<Product> findAllBySellerId(Long sellerId);

    // 판매자 내 상품 목록 (페이징) - GET /api/seller/products
    Page<Product> findAllBySellerId(Long sellerId, Pageable pageable);

    // 판매자 ID로 상품 상태 일괄 변경
    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.status = :status where p.seller.id = :sellerId")
    int updateStatusBySellerId(@Param("sellerId") Long sellerId, @Param("status") ProductStatus status);

    // 구매자용 조회

    // 검색/목록(키워드 FULLTEXT + 카테고리 + 가격 + 정렬)은 QueryDSL 동적 쿼리로 이동
    // → ProductRepositoryCustom.search() / ProductRepositoryImpl

    // 단건 상세 조회 (카테고리만 함께 — 자식 여럿 합치면 데이터 부풀어 느려짐)
    @EntityGraph(attributePaths = {"seller", "categoryMappings", "categoryMappings.category"})
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findWithCollectionsById(@Param("id") Long id);

    // 이미지 변경 시 낙관적 락 강제 증가 (자식 이미지 컬렉션 동시 변경 직렬화)
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForImageUpdate(@Param("id") Long id);

    // 연관 상품 — 같은 카테고리 / 자기 제외 / 상품·판매자 APPROVED
    // + 판매중(ON_SALE)·재고 있는 옵션이 하나라도 있는 상품만 (품절·STOP 제외)
    // + 인기순 정렬 (조회수 70% + 판매수 30%)
    // 카테고리 매칭을 JOIN+DISTINCT 대신 EXISTS로 처리 → 중복행 없음 + 계산식 ORDER BY가 DISTINCT 제약을 안 받음
    @Query("SELECT p FROM Product p JOIN p.seller s " +
           "WHERE p.id <> :excludeId " +
           "AND p.status = :productStatus AND s.status = :sellerStatus " +
           "AND EXISTS (SELECT 1 FROM ProductCategoryMapping m WHERE m.product = p AND m.category.id IN :categoryIds) " +
           "AND EXISTS (SELECT 1 FROM ProductItem i WHERE i.product = p AND i.status = :itemStatus AND i.stock > 0) " +
           "ORDER BY (p.viewCount * 0.7 + p.salesCount * 0.3) DESC")
    List<Product> findRelated(@Param("categoryIds") List<Long> categoryIds,
                              @Param("excludeId") Long excludeId,
                              @Param("productStatus") ProductStatus productStatus,
                              @Param("sellerStatus") SellerStatus sellerStatus,
                              @Param("itemStatus") ProductItemStatus itemStatus,
                              Pageable pageable);

    // 인기 상품
    @Query("SELECT p FROM Product p JOIN p.seller s " +
           "WHERE p.status = :productStatus AND s.status = :sellerStatus")
    Page<Product> findApproved(@Param("productStatus") ProductStatus productStatus,
                               @Param("sellerStatus") SellerStatus sellerStatus,
                               Pageable pageable);

    // 진행 중 주문 존재 여부 (상품 삭제 차단용)
    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi " +
           "WHERE oi.productItem.product.id = :productId " +
           "AND oi.status IN :statuses")
    boolean existsActiveOrderItemByProductId(@Param("productId") Long productId,
                                             @Param("statuses") List<OrderItemStatus> statuses);

    // 조회수 주간 일괄 초기화 (이미 0인 행은 건너뜀)
    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.viewCount = 0 where p.viewCount > 0")
    int resetAllViewCounts();

    // 승인된 상품 + 승인된 판매자의 태그 사용 빈도 집계 (인기 태그 자동완성용)
    @Query(value = "SELECT pt.tag, COUNT(pt.tag) AS cnt " +
                   "FROM product_tag pt " +
                   "INNER JOIN product p ON p.product_id = pt.product_id " +
                   "INNER JOIN seller s ON s.seller_id = p.seller_id " +
                   "WHERE p.status = 'APPROVED' AND s.status = 'APPROVED' " +
                   "GROUP BY pt.tag ORDER BY cnt DESC, pt.tag ASC LIMIT :limit",
           nativeQuery = true)
    List<Object[]> findTopTags(@Param("limit") int limit);

    // 인기/검색 목록 배치 fetch (seller·옵션 같이 로드)
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.seller LEFT JOIN FETCH p.productItems WHERE p.id IN :ids")
    List<Product> findAllByIdsWithItems(@Param("ids") List<Long> ids);

    // 인기 상품 판매수 시간 가중 집계 — 3일 윈도우, 1일 단위 3구간 (recent/middle/oldest)
    // CASE WHEN으로 구간별 quantity 합산 후 productId별 GROUP BY → 호출자가 가중치 적용
    @Query("SELECT pi.product.id AS productId, " +
           "SUM(CASE WHEN oi.createdAt >= :recentSince THEN oi.quantity ELSE 0 END) AS recent, " +
           "SUM(CASE WHEN oi.createdAt >= :middleSince AND oi.createdAt < :recentSince THEN oi.quantity ELSE 0 END) AS middle, " +
           "SUM(CASE WHEN oi.createdAt < :middleSince THEN oi.quantity ELSE 0 END) AS oldest " +
           "FROM OrderItem oi JOIN oi.productItem pi " +
           "WHERE oi.createdAt >= :oldestSince " +
           "AND oi.status IN :statuses " +
           "GROUP BY pi.product.id")
    List<SalesWeightedRow> aggregateSalesByTimeWindow(
        @Param("recentSince") LocalDateTime recentSince,
        @Param("middleSince") LocalDateTime middleSince,
        @Param("oldestSince") LocalDateTime oldestSince,
        @Param("statuses") List<OrderItemStatus> statuses
    );
}
