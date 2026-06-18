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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Locale;

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

        if (cond.tags() != null && !cond.tags().isEmpty()) {
            BooleanBuilder tagOr = new BooleanBuilder();
            for (String tag : cond.tags()) {
                if (tag == null) {
                    continue;
                }
                // 태그는 저장 시 trim+소문자(Locale.ROOT)로 정규화되므로 검색 입력도 동일하게 맞춰 매칭
                tagOr.or(product.tags.contains(tag.trim().toLowerCase(Locale.ROOT)));
            }
            where.and(tagOr);
        }

        // 정렬 무관 공통: 판매중(ON_SALE) 옵션이 (가격 필터가 있으면 그 범위 안에) 최소 1개 있는 상품만 노출.
        // 노출 최저가·가격필터·가격정렬이 모두 ON_SALE 동일 술어(onSalePriceWhere)를 공유하므로
        // 모든 옵션이 STOP이라 최저가가 0원으로 찍히는 노출, STOP 옵션 가격으로 필터를 통과해 판매중가로 보이는 불일치를 막는다.
        where.and(JPAExpressions.selectOne().from(productItem)
            .where(onSalePriceWhere(cond)).exists());

        return where;
    }

    // 가격 동률 시 페이지 간 순서가 흔들리지 않도록 id를 2차 정렬로 고정
    private OrderSpecifier<?>[] orderSpecifiers(ProductSearchCond cond) {
        return switch (cond.sort()) {
            case PRICE_ASC -> new OrderSpecifier<?>[]{
                new OrderSpecifier<>(Order.ASC, minOnSalePrice(cond)), product.id.desc()};
            case PRICE_DESC -> new OrderSpecifier<?>[]{
                new OrderSpecifier<>(Order.DESC, minOnSalePrice(cond)), product.id.desc()};
            // LATEST: 생성 시각 기준 최신순, 동시각이면 id로 안정 정렬
            default -> new OrderSpecifier<?>[]{product.createdAt.desc(), product.id.desc()};
        };
    }

    // 판매중(ON_SALE) 옵션의 최저가 — 가격 정렬 기준값
    // 가격 필터가 있으면 범위 내 옵션 기준으로 MIN → exists 필터와 정렬 기준 일치
    private Expression<Long> minOnSalePrice(ProductSearchCond cond) {
        return JPAExpressions.select(productItem.price.min())
            .from(productItem)
            .where(onSalePriceWhere(cond));
    }

    // 판매중(ON_SALE) 옵션 + (있으면) 가격 범위 — 노출 exists 필터·가격필터·가격정렬 MIN이 공유하는 공통 술어
    private BooleanBuilder onSalePriceWhere(ProductSearchCond cond) {
        BooleanBuilder where = new BooleanBuilder()
            .and(productItem.product.eq(product))
            .and(productItem.status.eq(ProductItemStatus.ON_SALE));
        if (cond.minPrice() != null) where.and(productItem.price.goe(cond.minPrice()));
        if (cond.maxPrice() != null) where.and(productItem.price.loe(cond.maxPrice()));
        return where;
    }
}
