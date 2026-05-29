package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {
}
