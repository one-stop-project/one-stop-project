package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
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

public interface ProductRepository extends JpaRepository<Product, Long> {

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

    // 검색/목록 (LATEST 등 가격 무관 정렬용)
    // 가격 필터: 옵션 가격 중 하나라도 [minPrice, maxPrice] 범위 포함 시 노출
    @Query("SELECT p FROM Product p JOIN p.seller s " +
           "WHERE p.status = :productStatus AND s.status = :sellerStatus " +
           "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:categoryId IS NULL OR EXISTS (" +
           "    SELECT m FROM ProductCategoryMapping m WHERE m.product = p AND m.category.id = :categoryId)) " +
           "AND (:minPrice IS NULL OR EXISTS (" +
           "    SELECT 1 FROM ProductItem pi WHERE pi.product = p AND pi.price >= :minPrice)) " +
           "AND (:maxPrice IS NULL OR EXISTS (" +
           "    SELECT 1 FROM ProductItem pi WHERE pi.product = p AND pi.price <= :maxPrice))")
    Page<Product> searchApproved(@Param("productStatus") ProductStatus productStatus,
                                 @Param("sellerStatus") SellerStatus sellerStatus,
                                 @Param("keyword") String keyword,
                                 @Param("categoryId") Long categoryId,
                                 @Param("minPrice") Long minPrice,
                                 @Param("maxPrice") Long maxPrice,
                                 Pageable pageable);

    // 검색/목록 — PRICE_ASC: MIN(price) 오름차순 (ON_SALE 옵션만 정렬 대상)
    @Query(value = "SELECT p FROM Product p JOIN p.seller s JOIN p.productItems pi " +
                   "WHERE p.status = :productStatus AND s.status = :sellerStatus " +
                   "AND pi.status = com.sparta.one_stop.global.enums.product.ProductItemStatus.ON_SALE " +
                   "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                   "AND (:categoryId IS NULL OR EXISTS (" +
                   "    SELECT m FROM ProductCategoryMapping m WHERE m.product = p AND m.category.id = :categoryId)) " +
                   "AND (:minPrice IS NULL OR pi.price >= :minPrice) " +
                   "AND (:maxPrice IS NULL OR pi.price <= :maxPrice) " +
                   "GROUP BY p " +
                   "ORDER BY MIN(pi.price) ASC",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Product p JOIN p.seller s JOIN p.productItems pi " +
                        "WHERE p.status = :productStatus AND s.status = :sellerStatus " +
                        "AND pi.status = com.sparta.one_stop.global.enums.product.ProductItemStatus.ON_SALE " +
                        "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                        "AND (:categoryId IS NULL OR EXISTS (" +
                        "    SELECT m FROM ProductCategoryMapping m WHERE m.product = p AND m.category.id = :categoryId)) " +
                        "AND (:minPrice IS NULL OR pi.price >= :minPrice) " +
                        "AND (:maxPrice IS NULL OR pi.price <= :maxPrice)")
    Page<Product> searchApprovedOrderByMinPriceAsc(@Param("productStatus") ProductStatus productStatus,
                                                   @Param("sellerStatus") SellerStatus sellerStatus,
                                                   @Param("keyword") String keyword,
                                                   @Param("categoryId") Long categoryId,
                                                   @Param("minPrice") Long minPrice,
                                                   @Param("maxPrice") Long maxPrice,
                                                   Pageable pageable);

    // 검색/목록 — PRICE_DESC: MIN(price) 내림차순
    @Query(value = "SELECT p FROM Product p JOIN p.seller s JOIN p.productItems pi " +
                   "WHERE p.status = :productStatus AND s.status = :sellerStatus " +
                   "AND pi.status = com.sparta.one_stop.global.enums.product.ProductItemStatus.ON_SALE " +
                   "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                   "AND (:categoryId IS NULL OR EXISTS (" +
                   "    SELECT m FROM ProductCategoryMapping m WHERE m.product = p AND m.category.id = :categoryId)) " +
                   "AND (:minPrice IS NULL OR pi.price >= :minPrice) " +
                   "AND (:maxPrice IS NULL OR pi.price <= :maxPrice) " +
                   "GROUP BY p " +
                   "ORDER BY MIN(pi.price) DESC",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Product p JOIN p.seller s JOIN p.productItems pi " +
                        "WHERE p.status = :productStatus AND s.status = :sellerStatus " +
                        "AND pi.status = com.sparta.one_stop.global.enums.product.ProductItemStatus.ON_SALE " +
                        "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                        "AND (:categoryId IS NULL OR EXISTS (" +
                        "    SELECT m FROM ProductCategoryMapping m WHERE m.product = p AND m.category.id = :categoryId)) " +
                        "AND (:minPrice IS NULL OR pi.price >= :minPrice) " +
                        "AND (:maxPrice IS NULL OR pi.price <= :maxPrice)")
    Page<Product> searchApprovedOrderByMinPriceDesc(@Param("productStatus") ProductStatus productStatus,
                                                    @Param("sellerStatus") SellerStatus sellerStatus,
                                                    @Param("keyword") String keyword,
                                                    @Param("categoryId") Long categoryId,
                                                    @Param("minPrice") Long minPrice,
                                                    @Param("maxPrice") Long maxPrice,
                                                    Pageable pageable);

    // 단건 상세 조회
    @EntityGraph(attributePaths = {"seller", "productItems", "productImages", "categoryMappings", "categoryMappings.category"})
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findWithCollectionsById(@Param("id") Long id);

    // 이미지 변경 시 낙관적 락 강제 증가 (자식 이미지 컬렉션 동시 변경 직렬화)
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForImageUpdate(@Param("id") Long id);

    // 연관 상품
    @Query("SELECT DISTINCT p FROM Product p JOIN p.seller s JOIN p.categoryMappings m " +
           "WHERE m.category.id IN :categoryIds AND p.id <> :excludeId " +
           "AND p.status = :productStatus AND s.status = :sellerStatus")
    List<Product> findRelated(@Param("categoryIds") List<Long> categoryIds,
                              @Param("excludeId") Long excludeId,
                              @Param("productStatus") ProductStatus productStatus,
                              @Param("sellerStatus") SellerStatus sellerStatus,
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

    // 인기 상품 응답용 배치 fetch (productItems 같이 로드)
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.productItems WHERE p.id IN :ids")
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
