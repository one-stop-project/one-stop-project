package com.sparta.one_stop.domain.coupon.dto.response;

import java.util.List;

public record MyCouponPageResponse(

    List<MyCouponResponse> content,

    int page,

    int size,

    long totalElements,

    int totalPages

) {
}
