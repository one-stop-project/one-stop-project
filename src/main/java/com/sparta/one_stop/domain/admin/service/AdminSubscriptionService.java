package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.admin.dto.AdminSubscriptionResponse;
import com.sparta.one_stop.domain.admin.dto.AdminSubscriptionStatsResponse;
import com.sparta.one_stop.domain.subscription.repository.SubscriptionRepository;
import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    public AdminSubscriptionStatsResponse getStats() {
        long active = subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE);
        long cancelled = subscriptionRepository.countByStatus(SubscriptionStatus.CANCELLED);
        long expired = subscriptionRepository.countByStatus(SubscriptionStatus.EXPIRED);
        return new AdminSubscriptionStatsResponse(active, cancelled, expired, active + cancelled + expired);
    }

    public Page<AdminSubscriptionResponse> getSubscriptions(Pageable pageable) {
        return subscriptionRepository.findAll(pageable)
            .map(AdminSubscriptionResponse::from);
    }
}
