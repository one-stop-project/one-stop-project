package com.sparta.one_stop.domain.admin.dto;

import com.sparta.one_stop.global.enums.coupon.CouponDiscountType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record AdminCouponCreateRequest(

    @NotBlank @Size(max = 100)
    String name,

    @NotNull
    CouponDiscountType discountType,

    @NotNull @Min(1)
    Integer discountValue,

    @NotNull @Min(0)
    Long minOrderPrice,

    // RATE 타입 필수, FIXED 타입 null
    Long maxDiscountPrice,

    @NotNull @Min(1)
    Integer totalQuantity,

    @NotNull
    LocalDateTime startAt,

    @NotNull
    LocalDateTime expiredAt
) {}
