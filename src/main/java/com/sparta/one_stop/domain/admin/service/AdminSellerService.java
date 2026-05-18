package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSellerService {

    private final SellerRepository sellerRepository;
    private final ProductRepository productRepository;

    // 대기 중인 판매자 목록 조회
    public List<Seller> getPendingSellers() {
        return sellerRepository.findAllByStatus(SellerStatus.PENDING);
    }

    // 판매자 승인
    @Transactional
    public void approveSeller(Long sellerId) {
        Seller seller = sellerRepository.findById(sellerId)
            .orElseThrow(() -> new CustomException(ErrorCode.SELLER_001));

        if (seller.getStatus() == SellerStatus.APPROVED) {
            throw new CustomException(ErrorCode.ADMIN_002);
        }

        seller.approve();
    }

    // 판매자 반려
    @Transactional
    public void rejectSeller(Long sellerId) {
        Seller seller = sellerRepository.findById(sellerId)
            .orElseThrow(() -> new CustomException(ErrorCode.SELLER_001));

        if (seller.getStatus() == SellerStatus.REJECTED) {
            throw new CustomException(ErrorCode.ADMIN_003);
        }

        seller.reject();
    }

    // 판매자 강제 비활성화
    @Transactional
    public void forceInactiveSeller(Long sellerId) {
        Seller seller = sellerRepository.findById(sellerId)
            .orElseThrow(() -> new CustomException(ErrorCode.SELLER_001));

        if (seller.getUser().isSuspended()) {
            throw new CustomException(ErrorCode.ADMIN_004);
        }

        seller.getUser().suspend();

        // 배치 업데이트로 N+1 해소
        productRepository.updateStatusBySellerId(seller.getId(), ProductStatus.FORCE_INACTIVE);
    }
}
