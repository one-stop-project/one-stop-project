package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.InventoryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryHistoryRepository extends JpaRepository<InventoryHistory, Long> {
}
