package com.sparta.one_stop.domain.coupon.service;

import com.sparta.one_stop.domain.coupon.dto.CouponDiscountResult;
import com.sparta.one_stop.domain.coupon.dto.response.AvailableCouponResponse;
import com.sparta.one_stop.domain.coupon.dto.response.MyCouponPageResponse;
import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
import com.sparta.one_stop.domain.coupon.repository.CouponRepository;
import com.sparta.one_stop.domain.coupon.repository.UserCouponRepository;
import com.sparta.one_stop.global.enums.coupon.CouponDiscountType;
import com.sparta.one_stop.global.enums.coupon.CouponStatus;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;
import com.sparta.one_stop.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponQueryServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @InjectMocks
    private CouponQueryService couponQueryService;

    @Test
    @DisplayName("getAvailableCoupons 성공 - 발급 가능한 쿠폰 목록을 조회한다")
    void getAvailableCoupons_success() {
        // given
        Coupon coupon = availableCoupon(10L);

        when(couponRepository.findAvailableCoupons(
            eq(CouponStatus.ACTIVE),
            any(LocalDateTime.class)
        )).thenReturn(List.of(coupon));

        // when
        List<AvailableCouponResponse> result = couponQueryService.getAvailableCoupons();

        // then
        assertThat(result).hasSize(1);

        AvailableCouponResponse response = result.get(0);

        assertThat(response.couponId()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("테스트 쿠폰");
        assertThat(response.discountType()).isEqualTo(CouponDiscountType.FIXED);
        assertThat(response.discountValue()).isEqualTo(1000);
        assertThat(response.minOrderPrice()).isEqualTo(0L);
        assertThat(response.maxDiscountPrice()).isNull();
        assertThat(response.remainingQuantity()).isEqualTo(90);

        verify(couponRepository).findAvailableCoupons(
            eq(CouponStatus.ACTIVE),
            any(LocalDateTime.class)
        );
    }

    @Test
    @DisplayName("getAvailableCoupons 성공 - 발급 가능한 쿠폰이 없으면 빈 목록을 반환한다")
    void getAvailableCoupons_success_whenEmpty() {
        when(couponRepository.findAvailableCoupons(
            eq(CouponStatus.ACTIVE),
            any(LocalDateTime.class)
        )).thenReturn(List.of());

        List<AvailableCouponResponse> result = couponQueryService.getAvailableCoupons();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getMyCoupons 성공 - status가 없으면 내 쿠폰 전체 목록을 조회한다")
    void getMyCoupons_success_withoutStatus() {
        // given
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 20);

        UserCoupon userCoupon = myUserCoupon(
            100L,
            10L,
            UserCouponStatus.AVAILABLE
        );

        Page<UserCoupon> userCouponPage = new PageImpl<>(
            List.of(userCoupon),
            pageable,
            1
        );

        when(userCouponRepository.findAllByUserId(userId, pageable))
            .thenReturn(userCouponPage);

        // when
        MyCouponPageResponse result = couponQueryService.getMyCoupons(
            userId,
            null,
            pageable
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(1);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);

        assertThat(result.content().get(0).userCouponId()).isEqualTo(100L);
        assertThat(result.content().get(0).couponId()).isEqualTo(10L);
        assertThat(result.content().get(0).couponName()).isEqualTo("테스트 쿠폰");
        assertThat(result.content().get(0).status()).isEqualTo(UserCouponStatus.AVAILABLE);

        verify(userCouponRepository).findAllByUserId(userId, pageable);
        verify(userCouponRepository, never()).findAllByUserIdAndStatus(
            any(),
            any(),
            any()
        );
    }

    @Test
    @DisplayName("getMyCoupons 성공 - status가 있으면 상태별 내 쿠폰 목록을 조회한다")
    void getMyCoupons_success_withStatus() {
        // given
        Long userId = 1L;
        UserCouponStatus status = UserCouponStatus.USED;
        Pageable pageable = PageRequest.of(0, 20);

        UserCoupon userCoupon = myUserCoupon(
            100L,
            10L,
            status
        );

        Page<UserCoupon> userCouponPage = new PageImpl<>(
            List.of(userCoupon),
            pageable,
            1
        );

        when(userCouponRepository.findAllByUserIdAndStatus(
            userId,
            status,
            pageable
        )).thenReturn(userCouponPage);

        // when
        MyCouponPageResponse result = couponQueryService.getMyCoupons(
            userId,
            status,
            pageable
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).status()).isEqualTo(UserCouponStatus.USED);

        verify(userCouponRepository).findAllByUserIdAndStatus(
            userId,
            status,
            pageable
        );
        verify(userCouponRepository, never()).findAllByUserId(userId, pageable);
    }

    @Test
    @DisplayName("getMyCoupons 실패 - userId가 null이면 인증 예외가 발생한다")
    void getMyCoupons_fail_whenUserIdIsNull() {
        // given
        Pageable pageable = PageRequest.of(0, 20);

        // when & then
        assertThatThrownBy(() -> couponQueryService.getMyCoupons(
            null,
            null,
            pageable
        )).isInstanceOf(CustomException.class);

        verifyNoInteractions(userCouponRepository);
    }

    @Test
    @DisplayName("validateAndCalculateDiscount 성공 - 쿠폰 검증 후 할인 금액을 계산한다")
    void validateAndCalculateDiscount_success() {
        // given
        Long userId = 1L;
        Long userCouponId = 100L;
        Long totalPrice = 10000L;
        Long discountPrice = 1000L;

        UserCoupon userCoupon = mock(UserCoupon.class);
        Coupon coupon = mock(Coupon.class);

        when(userCouponRepository.findByIdAndUserId(userCouponId, userId))
            .thenReturn(Optional.of(userCoupon));

        when(userCoupon.getCoupon())
            .thenReturn(coupon);
        when(coupon.calculateDiscountPrice(totalPrice))
            .thenReturn(discountPrice);

        // when
        CouponDiscountResult result = couponQueryService.validateAndCalculateDiscount(
            userId,
            userCouponId,
            totalPrice
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.userCoupon()).isEqualTo(userCoupon);
        assertThat(result.discountPrice()).isEqualTo(discountPrice);

        InOrder inOrder = inOrder(userCoupon, coupon);
        inOrder.verify(userCoupon).validateUsable(
            eq(userId),
            any(LocalDateTime.class)
        );
        inOrder.verify(coupon).calculateDiscountPrice(totalPrice);
    }

    @Test
    @DisplayName("validateAndCalculateDiscount 성공 - userCouponId가 null이면 쿠폰 미사용 결과를 반환한다")
    void validateAndCalculateDiscount_success_whenUserCouponIdIsNull() {
        // given
        Long userId = 1L;
        Long totalPrice = 10000L;

        // when
        CouponDiscountResult result = couponQueryService.validateAndCalculateDiscount(
            userId,
            null,
            totalPrice
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.userCoupon()).isNull();
        assertThat(result.discountPrice()).isEqualTo(0L);

        verifyNoInteractions(userCouponRepository);
    }

    @Test
    @DisplayName("validateAndCalculateDiscount 실패 - userId가 null이면 인증 예외가 발생한다")
    void validateAndCalculateDiscount_fail_whenUserIdIsNull() {
        // given
        Long userCouponId = 100L;
        Long totalPrice = 10000L;

        // when & then
        assertThatThrownBy(() -> couponQueryService.validateAndCalculateDiscount(
            null,
            userCouponId,
            totalPrice
        )).isInstanceOf(CustomException.class);

        verifyNoInteractions(userCouponRepository);
    }

    @Test
    @DisplayName("validateAndCalculateDiscount 실패 - totalPrice가 null이면 예외가 발생한다")
    void validateAndCalculateDiscount_fail_whenTotalPriceIsNull() {
        // given
        Long userId = 1L;
        Long userCouponId = 100L;

        // when & then
        assertThatThrownBy(() -> couponQueryService.validateAndCalculateDiscount(
            userId,
            userCouponId,
            null
        )).isInstanceOf(CustomException.class);

        verifyNoInteractions(userCouponRepository);
    }

    @Test
    @DisplayName("validateAndCalculateDiscount 실패 - totalPrice가 음수이면 예외가 발생한다")
    void validateAndCalculateDiscount_fail_whenTotalPriceIsNegative() {
        // given
        Long userId = 1L;
        Long userCouponId = 100L;
        Long totalPrice = -1L;

        // when & then
        assertThatThrownBy(() -> couponQueryService.validateAndCalculateDiscount(
            userId,
            userCouponId,
            totalPrice
        )).isInstanceOf(CustomException.class);

        verifyNoInteractions(userCouponRepository);
    }

    @Test
    @DisplayName("validateAndCalculateDiscount 실패 - 사용자 쿠폰을 찾을 수 없으면 예외가 발생한다")
    void validateAndCalculateDiscount_fail_whenUserCouponNotFound() {
        // given
        Long userId = 1L;
        Long userCouponId = 100L;
        Long totalPrice = 10000L;

        when(userCouponRepository.findByIdAndUserId(userCouponId, userId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponQueryService.validateAndCalculateDiscount(
            userId,
            userCouponId,
            totalPrice
        )).isInstanceOf(CustomException.class);

        verify(userCouponRepository).findByIdAndUserId(userCouponId, userId);
    }

    @Test
    @DisplayName("validateAndCalculateDiscount 실패 - 쿠폰 사용 가능 검증에 실패하면 예외가 발생한다")
    void validateAndCalculateDiscount_fail_whenUserCouponIsNotUsable() {
        // given
        Long userId = 1L;
        Long userCouponId = 100L;
        Long totalPrice = 10000L;

        UserCoupon userCoupon = mock(UserCoupon.class);

        when(userCouponRepository.findByIdAndUserId(userCouponId, userId))
            .thenReturn(Optional.of(userCoupon));

        doThrow(CustomException.class)
            .when(userCoupon)
            .validateUsable(
                eq(userId),
                any(LocalDateTime.class)
            );

        // when & then
        assertThatThrownBy(() -> couponQueryService.validateAndCalculateDiscount(
            userId,
            userCouponId,
            totalPrice
        )).isInstanceOf(CustomException.class);

        verify(userCoupon).validateUsable(
            eq(userId),
            any(LocalDateTime.class)
        );
        verify(userCoupon, never()).getCoupon();
    }

    @Test
    @DisplayName("validateAndCalculateDiscount 실패 - 쿠폰 할인 금액 계산에 실패하면 예외가 발생한다")
    void validateAndCalculateDiscount_fail_whenCalculateDiscountPriceFails() {
        // given
        Long userId = 1L;
        Long userCouponId = 100L;
        Long totalPrice = 5000L;

        UserCoupon userCoupon = mock(UserCoupon.class);
        Coupon coupon = mock(Coupon.class);

        when(userCouponRepository.findByIdAndUserId(userCouponId, userId))
            .thenReturn(Optional.of(userCoupon));
        when(userCoupon.getCoupon())
            .thenReturn(coupon);
        when(coupon.calculateDiscountPrice(totalPrice))
            .thenThrow(CustomException.class);

        // when & then
        assertThatThrownBy(() -> couponQueryService.validateAndCalculateDiscount(
            userId,
            userCouponId,
            totalPrice
        )).isInstanceOf(CustomException.class);

        InOrder inOrder = inOrder(userCoupon, coupon);
        inOrder.verify(userCoupon).validateUsable(
            eq(userId),
            any(LocalDateTime.class)
        );
        inOrder.verify(userCoupon).getCoupon();
        inOrder.verify(coupon).calculateDiscountPrice(totalPrice);
    }

    private Coupon availableCoupon(Long couponId) {
        Coupon coupon = mock(Coupon.class);

        when(coupon.getId())
            .thenReturn(couponId);
        when(coupon.getName())
            .thenReturn("테스트 쿠폰");
        when(coupon.getDiscountType())
            .thenReturn(CouponDiscountType.FIXED);
        when(coupon.getDiscountValue())
            .thenReturn(1000);
        when(coupon.getMinOrderPrice())
            .thenReturn(0L);
        when(coupon.getMaxDiscountPrice())
            .thenReturn(null);
        when(coupon.getTotalQuantity())
            .thenReturn(100);
        when(coupon.getIssuedQuantity())
            .thenReturn(10);
        when(coupon.getStartAt())
            .thenReturn(LocalDateTime.now().minusDays(1));
        when(coupon.getExpiredAt())
            .thenReturn(LocalDateTime.now().plusDays(30));

        return coupon;
    }

    private UserCoupon myUserCoupon(
        Long userCouponId,
        Long couponId,
        UserCouponStatus status
    ) {
        Coupon coupon = couponForMyCoupon(couponId);
        UserCoupon userCoupon = mock(UserCoupon.class);

        when(userCoupon.getId())
            .thenReturn(userCouponId);
        when(userCoupon.getCoupon())
            .thenReturn(coupon);
        when(userCoupon.getStatus())
            .thenReturn(status);
        when(userCoupon.getCreatedAt())
            .thenReturn(LocalDateTime.now().minusDays(1));
        when(userCoupon.getUsedAt())
            .thenReturn(null);

        return userCoupon;
    }

    private Coupon couponForMyCoupon(Long couponId) {
        Coupon coupon = mock(Coupon.class);

        when(coupon.getId())
            .thenReturn(couponId);
        when(coupon.getName())
            .thenReturn("테스트 쿠폰");
        when(coupon.getDiscountType())
            .thenReturn(CouponDiscountType.FIXED);
        when(coupon.getDiscountValue())
            .thenReturn(1000);
        when(coupon.getMinOrderPrice())
            .thenReturn(0L);
        when(coupon.getMaxDiscountPrice())
            .thenReturn(null);
        when(coupon.getExpiredAt())
            .thenReturn(LocalDateTime.now().plusDays(30));

        return coupon;
    }

}
