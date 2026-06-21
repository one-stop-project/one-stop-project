package com.sparta.one_stop.domain.seller.repository;

import com.sparta.one_stop.domain.seller.dto.response.SellerReviewResponse;
import com.sparta.one_stop.domain.seller.dto.response.SellerReviewSummaryResponse;
import com.sparta.one_stop.global.enums.review.ReviewStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SellerReviewQueryRepository {

    private final EntityManager entityManager;

    public Page<SellerReviewResponse> findReviews(Long sellerId, Long productId, Pageable pageable) {
        String productFilter = productId == null ? "" : " and p.id = :productId ";
        String filter = """
            from Review r
            join r.product p
            join r.user u
            join r.orderItem oi
            left join r.images ri
            where p.seller.id = :sellerId and r.status = :status
            """ + productFilter;

        var query = entityManager.createQuery("""
                select new com.sparta.one_stop.domain.seller.dto.response.SellerReviewResponse(
                    r.id, p.id, p.name, p.thumbnailUrl, oi.id, u.name,
                    r.rating, r.content, count(ri.id), r.createdAt
                )
                """ + filter + """
                group by r.id, p.id, p.name, p.thumbnailUrl, oi.id, u.name,
                         r.rating, r.content, r.createdAt
                order by r.createdAt desc, r.id desc
                """, SellerReviewResponse.class)
            .setParameter("sellerId", sellerId)
            .setParameter("status", ReviewStatus.ACTIVE);

        String countFilter = """
            from Review r join r.product p
            where p.seller.id = :sellerId and r.status = :status
            """ + productFilter;
        var countQuery = entityManager.createQuery("select count(r) " + countFilter, Long.class)
            .setParameter("sellerId", sellerId)
            .setParameter("status", ReviewStatus.ACTIVE);

        if (productId != null) {
            query.setParameter("productId", productId);
            countQuery.setParameter("productId", productId);
        }

        List<SellerReviewResponse> content = query
            .setFirstResult(Math.toIntExact(pageable.getOffset()))
            .setMaxResults(pageable.getPageSize())
            .getResultList();
        return new PageImpl<>(content, pageable, countQuery.getSingleResult());
    }

    public SellerReviewSummaryResponse getSummary(Long sellerId) {
        List<SellerReviewSummaryResponse> result = entityManager.createQuery("""
                select new com.sparta.one_stop.domain.seller.dto.response.SellerReviewSummaryResponse(
                    count(r), avg(r.rating),
                    sum(case when r.rating = 5 then 1 else 0 end),
                    sum(case when r.rating = 4 then 1 else 0 end),
                    sum(case when r.rating = 3 then 1 else 0 end),
                    sum(case when r.rating = 2 then 1 else 0 end),
                    sum(case when r.rating = 1 then 1 else 0 end)
                )
                from Review r join r.product p
                where p.seller.id = :sellerId and r.status = :status
                """, SellerReviewSummaryResponse.class)
            .setParameter("sellerId", sellerId)
            .setParameter("status", ReviewStatus.ACTIVE)
            .getResultList();

        return result.isEmpty()
            ? new SellerReviewSummaryResponse(0L, 0.0, 0L, 0L, 0L, 0L, 0L)
            : result.get(0);
    }
}
