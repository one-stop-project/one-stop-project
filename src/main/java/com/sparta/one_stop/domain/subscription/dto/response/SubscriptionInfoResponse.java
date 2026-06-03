package com.sparta.one_stop.domain.subscription.dto.response;

import java.util.List;

public record SubscriptionInfoResponse(
    String name,
    Integer price,
    String billingCycle,
    List<String> benefits
) {
}
