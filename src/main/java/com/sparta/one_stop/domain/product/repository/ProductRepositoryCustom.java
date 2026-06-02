package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// QueryDSL 동적 검색 — 정렬(LATEST/PRICE_ASC/PRICE_DESC)·키워드(FULLTEXT)·카테고리·가격 조합
public interface ProductRepositoryCustom {

    Page<Product> search(ProductSearchCond cond, Pageable pageable);
}
