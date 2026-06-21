package com.sparta.one_stop.domain.seller.repository;

import com.sparta.one_stop.domain.seller.dto.response.SellerProductSalesStatResponse;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SellerDashboardQueryRepository {

    private final EntityManager entityManager;

    public List<Object[]> countByStatus(Long sellerId, OrderItemStatus excludedStatus) {
        return entityManager.createQuery("""
                select oi.status, count(oi)
                from OrderItem oi
                where oi.seller.id = :sellerId and oi.status <> :excludedStatus
                group by oi.status
                """, Object[].class)
            .setParameter("sellerId", sellerId)
            .setParameter("excludedStatus", excludedStatus)
            .getResultList();
    }

    public Page<SellerProductSalesStatResponse> findProductSalesStats(
        Long sellerId,
        LocalDateTime orderedFromInclusive,
        LocalDateTime orderedToExclusive,
        Pageable pageable
    ) {
        String filter = """
            from OrderItem oi
            join oi.productItem pi
            join pi.product p
            where oi.seller.id = :sellerId
              and oi.status = :status
              and oi.createdAt >= :fromInclusive
              and oi.createdAt < :toExclusive
            """;

        List<SellerProductSalesStatResponse> content = entityManager.createQuery("""
                select new com.sparta.one_stop.domain.seller.dto.response.SellerProductSalesStatResponse(
                    p.id, p.name, p.thumbnailUrl, sum(oi.quantity), sum(oi.price * oi.quantity)
                )
                """ + filter + """
                group by p.id, p.name, p.thumbnailUrl
                order by sum(oi.quantity) desc, p.id desc
                """, SellerProductSalesStatResponse.class)
            .setParameter("sellerId", sellerId)
            .setParameter("status", OrderItemStatus.DELIVERED)
            .setParameter("fromInclusive", orderedFromInclusive)
            .setParameter("toExclusive", orderedToExclusive)
            .setFirstResult(toOffset(pageable))
            .setMaxResults(pageable.getPageSize())
            .getResultList();

        long total = entityManager.createQuery("select count(distinct p.id) " + filter, Long.class)
            .setParameter("sellerId", sellerId)
            .setParameter("status", OrderItemStatus.DELIVERED)
            .setParameter("fromInclusive", orderedFromInclusive)
            .setParameter("toExclusive", orderedToExclusive)
            .getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    private int toOffset(Pageable pageable) {
        return Math.toIntExact(pageable.getOffset());
    }
}
