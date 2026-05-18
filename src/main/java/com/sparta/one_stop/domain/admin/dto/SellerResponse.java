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
        // 사업자등록번호 마스킹
        String businessNumber = seller.getBusinessNumber();
        String masked = businessNumber.substring(0, 3) + "-****-"
            + businessNumber.substring(businessNumber.length() - 2);

        return new SellerResponse(
            seller.getId(),
            seller.getUser().getId(),
            seller.getShopName(),
            seller.getBusinessNumber(),
            seller.getStatus()
        );
    }
}
