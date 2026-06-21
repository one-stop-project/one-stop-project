package com.sparta.one_stop.domain.seller.service;

import com.sparta.one_stop.domain.admin.entity.AdminActionHistory;
import com.sparta.one_stop.domain.admin.repository.AdminActionHistoryRepository;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.seller.dto.response.SellerProductRejectReasonResponse;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.enums.admin.AdminActionTarget;
import com.sparta.one_stop.global.enums.admin.AdminActionType;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerProductInsightService {

    private final SellerReader sellerReader;
    private final ProductRepository productRepository;
    private final AdminActionHistoryRepository adminActionHistoryRepository;

    public SellerProductRejectReasonResponse getRejectReason(Long userId, Long productId) {
        Seller seller = sellerReader.getApprovedSeller(userId);
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));
        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new CustomException(ErrorCode.PRODUCT_008);
        }
        if (product.getStatus() != ProductStatus.REJECTED) {
            throw new CustomException(ErrorCode.PRODUCT_010, "반려 상태의 상품만 조회할 수 있습니다");
        }
        AdminActionHistory rejection = adminActionHistoryRepository
            .findTopByTargetTypeAndTargetIdAndActionOrderByCreatedAtDesc(
                AdminActionTarget.PRODUCT, productId, AdminActionType.REJECT)
            .orElse(null);
        return SellerProductRejectReasonResponse.of(product, rejection);
    }
}
