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

    // 검색/목록
    @Query("SELECT p FROM Product p JOIN p.seller s " +
           "WHERE p.status = :productStatus AND s.status = :sellerStatus " +
           "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:categoryId IS NULL OR EXISTS (" +
           "    SELECT m FROM ProductCategoryMapping m WHERE m.product = p AND m.category.id = :categoryId))")
    Page<Product> searchApproved(@Param("productStatus") ProductStatus productStatus,
                                 @Param("sellerStatus") SellerStatus sellerStatus,
                                 @Param("keyword") String keyword,
                                 @Param("categoryId") Long categoryId,
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

    // 진행 중 주문 존재 여부 (상품 삭제 차단용 — PRODUCT_009)
    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi " +
           "WHERE oi.productItem.product.id = :productId " +
           "AND oi.status IN :statuses")
    boolean existsActiveOrderItemByProductId(@Param("productId") Long productId,
                                             @Param("statuses") List<OrderItemStatus> statuses);
}
