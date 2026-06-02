package com.sparta.one_stop.domain.product.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.QProduct;
import com.sparta.one_stop.domain.product.entity.QProductCategoryMapping;
import com.sparta.one_stop.domain.product.entity.QProductItem;
import com.sparta.one_stop.global.enums.product.ProductItemStatus;
import com.sparta.one_stop.global.enums.product.SortType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QProduct product = QProduct.product;
    private static final QProductItem productItem = QProductItem.productItem;
    private static final QProductCategoryMapping mapping = QProductCategoryMapping.productCategoryMapping;

    @Override
    public Page<Product> search(ProductSearchCond cond, Pageable pageable) {
        BooleanBuilder where = buildWhere(cond);

        List<Product> content = queryFactory
            .selectFrom(product)
            .where(where)
            .orderBy(orderSpecifiers(cond))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        Long total = queryFactory
            .select(product.count())
            .from(product)
            .where(where)
            .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private BooleanBuilder buildWhere(ProductSearchCond cond) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(product.status.eq(cond.productStatus()));
        where.and(product.seller.status.eq(cond.sellerStatus()));

        // 키워드: name + description FULLTEXT (BOOLEAN MODE), 관련도 > 0 이면 매칭
        if (cond.keyword() != null) {
            where.and(Expressions.numberTemplate(Double.class,
                    "function('fulltext_match', {0}, {1}, {2})",
                    product.name, product.description, cond.keyword())
                .gt(0.0));
        }

        if (cond.categoryId() != null) {
            where.and(JPAExpressions.selectOne()
                .from(mapping)
                .where(mapping.product.eq(product)
                    .and(mapping.category.id.eq(cond.categoryId())))
                .exists());
        }

        if (isPriceSort(cond.sort())) {
            // 가격 정렬은 판매중(ON_SALE) 옵션만 대상 — 옵션 존재 보장 + 가격 범위도 ON_SALE 기준
            // exists 필터와 ORDER BY MIN이 동일 술어(onSalePriceWhere)를 공유해야 정렬 기준이 어긋나지 않음
            where.and(JPAExpressions.selectOne().from(productItem)
                .where(onSalePriceWhere(cond)).exists());
        } else if (cond.minPrice() != null || cond.maxPrice() != null) {
            // 최신순 등은 옵션 상태 무관 — 가격 범위에 드는 옵션이 하나라도 있으면 노출
            BooleanBuilder price = new BooleanBuilder().and(productItem.product.eq(product));
            if (cond.minPrice() != null) price.and(productItem.price.goe(cond.minPrice()));
            if (cond.maxPrice() != null) price.and(productItem.price.loe(cond.maxPrice()));
            where.and(JPAExpressions.selectOne().from(productItem).where(price).exists());
        }

        return where;
    }

    // 가격 동률 시 페이지 간 순서가 흔들리지 않도록 id를 2차 정렬로 고정
    private OrderSpecifier<?>[] orderSpecifiers(ProductSearchCond cond) {
        return switch (cond.sort()) {
            case PRICE_ASC -> new OrderSpecifier<?>[]{
                new OrderSpecifier<>(Order.ASC, minOnSalePrice(cond)), product.id.desc()};
            case PRICE_DESC -> new OrderSpecifier<?>[]{
                new OrderSpecifier<>(Order.DESC, minOnSalePrice(cond)), product.id.desc()};
            default -> new OrderSpecifier<?>[]{product.id.desc()};
        };
    }

    // 판매중(ON_SALE) 옵션의 최저가 — 가격 정렬 기준값
    // 가격 필터가 있으면 범위 내 옵션 기준으로 MIN → exists 필터와 정렬 기준 일치
    private Expression<Long> minOnSalePrice(ProductSearchCond cond) {
        return JPAExpressions.select(productItem.price.min())
            .from(productItem)
            .where(onSalePriceWhere(cond));
    }

    // 판매중(ON_SALE) 옵션 + (있으면) 가격 범위 — 가격 정렬의 필터/정렬 공통 술어
    private BooleanBuilder onSalePriceWhere(ProductSearchCond cond) {
        BooleanBuilder where = new BooleanBuilder()
            .and(productItem.product.eq(product))
            .and(productItem.status.eq(ProductItemStatus.ON_SALE));
        if (cond.minPrice() != null) where.and(productItem.price.goe(cond.minPrice()));
        if (cond.maxPrice() != null) where.and(productItem.price.loe(cond.maxPrice()));
        return where;
    }

    private boolean isPriceSort(SortType sort) {
        return sort == SortType.PRICE_ASC || sort == SortType.PRICE_DESC;
    }
}
