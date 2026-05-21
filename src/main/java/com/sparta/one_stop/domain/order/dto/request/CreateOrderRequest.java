package com.sparta.one_stop.domain.order.dto.request;

import com.sparta.one_stop.global.enums.order.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(

    OrderType orderType,

    @Valid
    List<CreateOrderItemRequest> items,

    List<Long> cartItemIds,

    @NotBlank(message = "수령인 이름은 필수입니다.")
    String receiverName,

    @NotBlank(message = "수령인 연락처는 필수입니다.")
    String receiverPhone,

    @NotBlank(message = "배송 주소는 필수입니다.")
    String receiverAddress,

    @Size(max = 50, message = "배송 요청사항은 50자 이하로 입력해주세요.")
    String deliveryMessage,

    Long userCouponId,

    @Min(value = 0, message = "사용 포인트는 0 이상이어야 합니다.")
    Integer usedPoint
) {
}
