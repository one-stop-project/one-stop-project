package com.sparta.one_stop.domain.admin.dto;

import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.enums.user.SellerStatus;

// 판매자 승인/반려 응답 DTO
public record SellerResponse(
    Long sellerId,
    Long userId,
    String shopName,
    String businessNumber,
    SellerStatus status
) {
    public static SellerResponse from(Seller seller) {
        return new SellerResponse(
            seller.getId(),
            seller.getUser().getId(),
            seller.getShopName(),
            seller.getBusinessNumber(),
            seller.getStatus()
        );
    }
}
