package com.sparta.one_stop.domain.seller.dto.response;

import com.sparta.one_stop.domain.admin.entity.AdminActionHistory;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.enums.user.SellerStatus;

import java.time.LocalDateTime;

public record SellerMyStatusResponse(
    Long sellerId,
    String shopName,
    String businessNumber,
    SellerStatus sellerStatus,
    String rejectReason,
    LocalDateTime rejectedAt
) {
    public static SellerMyStatusResponse of(Seller seller, AdminActionHistory rejectHistory) {
        return new SellerMyStatusResponse(
            seller.getId(), seller.getShopName(), seller.getBusinessNumber(), seller.getStatus(),
            rejectHistory == null ? null : rejectHistory.getReason(),
            rejectHistory == null ? null : rejectHistory.getCreatedAt()
        );
    }
}
