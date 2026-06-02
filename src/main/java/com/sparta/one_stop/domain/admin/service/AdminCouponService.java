package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.admin.dto.AdminCouponCreateRequest;
import com.sparta.one_stop.domain.admin.dto.AdminCouponResponse;
import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.domain.coupon.repository.CouponRepository;
import com.sparta.one_stop.global.enums.coupon.CouponStatus;
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
public class AdminCouponService {

    private final CouponRepository couponRepository;

    @Transactional
    public AdminCouponResponse createCoupon(AdminCouponCreateRequest request) {
        Coupon coupon = new Coupon(
            request.name(),
            request.discountType(),
            request.discountValue(),
            request.minOrderPrice(),
            request.maxDiscountPrice(),
            request.totalQuantity(),
            request.startAt(),
            request.expiredAt()
        );
        return AdminCouponResponse.from(couponRepository.save(coupon));
    }

    public Page<AdminCouponResponse> getCoupons(CouponStatus status, Pageable pageable) {
        if (status == null) {
            return couponRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(AdminCouponResponse::from);
        }
        return couponRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable)
            .map(AdminCouponResponse::from);
    }

    @Transactional
    public AdminCouponResponse deactivateCoupon(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CustomException(ErrorCode.COUPON_004));
        coupon.deactivate();
        return AdminCouponResponse.from(coupon);
    }
}
