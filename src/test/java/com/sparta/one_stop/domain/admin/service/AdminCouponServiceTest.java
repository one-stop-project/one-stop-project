package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.admin.dto.AdminCouponCreateRequest;
import com.sparta.one_stop.domain.admin.dto.AdminCouponResponse;
import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.domain.coupon.repository.CouponRepository;
import com.sparta.one_stop.global.enums.coupon.CouponDiscountType;
import com.sparta.one_stop.global.enums.coupon.CouponStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminCouponService 테스트")
class AdminCouponServiceTest {

    @Mock private CouponRepository couponRepository;

    @InjectMocks
    private AdminCouponService adminCouponService;

    private static final Long COUPON_ID = 1L;

    private AdminCouponCreateRequest fixedRequest() {
        return new AdminCouponCreateRequest(
            "신규가입 쿠폰",
            CouponDiscountType.FIXED,
            3000,
            10000L,
            null,
            100,
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
    }

    private Coupon buildCoupon(CouponStatus status) {
        Coupon coupon = new Coupon(
            "테스트 쿠폰",
            CouponDiscountType.FIXED,
            1000,
            5000L,
            null,
            50,
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(30)
        );
        if (status == CouponStatus.INACTIVE) {
            coupon.deactivate();
        }
        return coupon;
    }

    // ─────────────────────────────────────────────────────────────
    //  쿠폰 생성
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createCoupon()")
    class CreateCoupon {

        @Test
        @DisplayName("유효한 요청으로 쿠폰 생성 → 저장된 쿠폰 응답 반환")
        void create_coupon_success() {
            Coupon savedCoupon = buildCoupon(CouponStatus.ACTIVE);
            given(couponRepository.save(any())).willReturn(savedCoupon);

            AdminCouponResponse response = adminCouponService.createCoupon(fixedRequest());

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(CouponStatus.ACTIVE);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  쿠폰 비활성화
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deactivateCoupon()")
    class DeactivateCoupon {

        @Test
        @DisplayName("ACTIVE 쿠폰 비활성화 → INACTIVE 상태로 변경된다")
        void deactivate_active_coupon_success() {
            Coupon coupon = buildCoupon(CouponStatus.ACTIVE);
            given(couponRepository.findById(COUPON_ID)).willReturn(Optional.of(coupon));

            AdminCouponResponse response = adminCouponService.deactivateCoupon(COUPON_ID);

            assertThat(response.status()).isEqualTo(CouponStatus.INACTIVE);
        }

        @Test
        @DisplayName("이미 INACTIVE 쿠폰 비활성화 → COUPON_010 예외")
        void deactivate_already_inactive_throws() {
            given(couponRepository.findById(COUPON_ID)).willReturn(Optional.of(buildCoupon(CouponStatus.INACTIVE)));

            assertThatThrownBy(() -> adminCouponService.deactivateCoupon(COUPON_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.COUPON_010));
        }

        @Test
        @DisplayName("존재하지 않는 쿠폰 비활성화 → COUPON_004 예외")
        void deactivate_not_found_throws() {
            given(couponRepository.findById(COUPON_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminCouponService.deactivateCoupon(COUPON_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.COUPON_004));
        }
    }
}
