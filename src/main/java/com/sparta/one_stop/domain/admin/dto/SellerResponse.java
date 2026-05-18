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
    // 한국 사업자등록번호 형식: 10자리 숫자 (예: 1234567890 또는 123-45-67890)
    private static String maskBusinessNumber(String businessNumber) {

        // null 체크
        if (businessNumber == null || businessNumber.isBlank()) {
            return "***-****-**";
        }

        // 하이픈 제거 후 순수 숫자만 추출
        String digits = businessNumber.replaceAll("[^0-9]", "");

        // 한국 사업자등록번호는 10자리
        if (digits.length() != 10) {
            return "***-****-**";
        }

        // 앞 3자리 - 마스킹 - 뒤 2자리
        return digits.substring(0, 3) + "-****-" + digits.substring(8);
    }
}
