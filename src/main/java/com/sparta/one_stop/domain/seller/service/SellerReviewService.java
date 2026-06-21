package com.sparta.one_stop.domain.seller.service;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.seller.dto.response.SellerReviewResponse;
import com.sparta.one_stop.domain.seller.dto.response.SellerReviewSummaryResponse;
import com.sparta.one_stop.domain.seller.repository.SellerReviewQueryRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerReviewService {

    private final SellerReader sellerReader;
    private final SellerReviewQueryRepository queryRepository;
    private final ProductRepository productRepository;
    private final SellerPagePolicy pagePolicy;

    public Page<SellerReviewResponse> getReviews(Long userId, Pageable pageable) {
        pagePolicy.validate(pageable);
        Seller seller = sellerReader.getApprovedSeller(userId);
        return queryRepository.findReviews(seller.getId(), null, pageable);
    }

    public Page<SellerReviewResponse> getProductReviews(Long userId, Long productId, Pageable pageable) {
        pagePolicy.validate(pageable);
        Seller seller = sellerReader.getApprovedSeller(userId);
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));
        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new CustomException(ErrorCode.PRODUCT_008);
        }
        return queryRepository.findReviews(seller.getId(), productId, pageable);
    }

    public SellerReviewSummaryResponse getReviewSummary(Long userId) {
        Seller seller = sellerReader.getApprovedSeller(userId);
        return queryRepository.getSummary(seller.getId());
    }
}
