package com.sparta.one_stop.domain.seller.service;

import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerReader {

    private final SellerRepository sellerRepository;

    public Seller getSeller(Long userId) {
        return sellerRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.SELLER_001));
    }

    public Seller getApprovedSeller(Long userId) {
        Seller seller = getSeller(userId);
        if (!seller.isApproved()) throw new CustomException(ErrorCode.SELLER_003);
        return seller;
    }
}
