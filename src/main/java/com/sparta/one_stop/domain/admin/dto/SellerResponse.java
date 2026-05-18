package com.sparta.one_stop.domain.admin.dto;

import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import lombok.extern.slf4j.Slf4j;

// 판매자 승인/반려 응답 DTO
@Slf4j
public record SellerResponse(
    Long sellerId,
    Long userId,
    String shopName,
    String businessNumber,
    SellerStatus status
) {
    private static final String MASKED_DEFAULT = "***-****-**";

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
        if (businessNumber == null || businessNumber.isBlank()) {
            log.warn("사업자등록번호가 비어있습니다.");
            return MASKED_DEFAULT;
        }

        String digits = businessNumber.replaceAll("[^0-9]", "");

        if (digits.length() != 10) {
            log.warn("올바르지 않은 사업자등록번호 포맷입니다. 길이: {}", digits.length());
            return MASKED_DEFAULT;
        }

        return digits.substring(0, 3) + "-****-" + digits.substring(8);
    }
}
