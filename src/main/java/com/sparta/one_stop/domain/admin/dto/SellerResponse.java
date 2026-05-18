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
            maskBusinessNumber(seller.getBusinessNumber()),
            seller.getStatus()
        );
    }

    // 사업자등록번호 마스킹 처리
    private static String maskBusinessNumber(String businessNumber) {
        if (businessNumber == null || businessNumber.length() < 5) {
            return "***-****-**";
        }
        return businessNumber.substring(0, 3) + "-****-"
            + businessNumber.substring(businessNumber.length() - 2);
    }
}
