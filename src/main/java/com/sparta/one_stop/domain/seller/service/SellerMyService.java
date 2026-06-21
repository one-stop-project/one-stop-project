package com.sparta.one_stop.domain.seller.service;

import com.sparta.one_stop.domain.admin.entity.AdminActionHistory;
import com.sparta.one_stop.domain.admin.repository.AdminActionHistoryRepository;
import com.sparta.one_stop.domain.seller.dto.response.SellerMyStatusResponse;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.enums.admin.AdminActionTarget;
import com.sparta.one_stop.global.enums.admin.AdminActionType;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerMyService {

    private final SellerReader sellerReader;
    private final AdminActionHistoryRepository adminActionHistoryRepository;

    public SellerMyStatusResponse getMySellerStatus(Long userId) {
        Seller seller = sellerReader.getSeller(userId);
        AdminActionHistory rejection = null;
        if (seller.getStatus() == SellerStatus.REJECTED) {
            rejection = adminActionHistoryRepository
                .findTopByTargetTypeAndTargetIdAndActionOrderByCreatedAtDesc(
                    AdminActionTarget.SELLER, seller.getId(), AdminActionType.REJECT)
                .orElse(null);
        }
        return SellerMyStatusResponse.of(seller, rejection);
    }
}
