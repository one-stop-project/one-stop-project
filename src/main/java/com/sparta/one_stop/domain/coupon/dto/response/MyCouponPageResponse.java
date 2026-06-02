package com.sparta.one_stop.domain.coupon.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record MyCouponPageResponse(

    List<MyCouponResponse> content,

    int page,

    int size,

    long totalElements,

    int totalPages

) {

    // 내 쿠폰 페이지 응답 생성
    // 이미 DTO로 변환된 MyCouponResponse Page를 페이징 응답으로 변환한다
    public static MyCouponPageResponse of(Page<MyCouponResponse> myCouponPage) {
        return new MyCouponPageResponse(
            myCouponPage.getContent(),
            myCouponPage.getNumber(),
            myCouponPage.getSize(),
            myCouponPage.getTotalElements(),
            myCouponPage.getTotalPages()
        );
    }

}
