package com.sparta.one_stop.domain.order.repository;

import com.sparta.one_stop.domain.order.entity.OrderCancelHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCancelHistoryRepository extends JpaRepository<OrderCancelHistory, Long> {

}
