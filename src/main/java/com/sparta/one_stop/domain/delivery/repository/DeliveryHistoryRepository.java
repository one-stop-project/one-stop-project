package com.sparta.one_stop.domain.delivery.repository;

import com.sparta.one_stop.domain.delivery.entity.DeliveryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryHistoryRepository extends JpaRepository<DeliveryHistory, Long> {

    List<DeliveryHistory> findAllByDeliveryIdOrderByChangedAtAsc(Long deliveryId);
}
