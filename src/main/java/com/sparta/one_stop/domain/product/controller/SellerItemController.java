package com.sparta.one_stop.domain.product.controller;

import com.sparta.one_stop.domain.product.dto.request.InboundRequest;
import com.sparta.one_stop.domain.product.dto.request.ItemUpdateRequest;
import com.sparta.one_stop.domain.product.dto.response.InboundResponse;
import com.sparta.one_stop.domain.product.dto.response.ItemUpdateResponse;
import com.sparta.one_stop.domain.product.service.SellerItemService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller - Item", description = "판매자 옵션/재고 관리 API")
@RestController
@RequestMapping("/api/seller/items")
@RequiredArgsConstructor
public class SellerItemController {

    private final SellerItemService sellerItemService;

    // 옵션 수정 (price / stock / status)
    @Operation(summary = "옵션 수정")
    @PatchMapping("/{itemId}")
    public ResponseEntity<ApiResponse<ItemUpdateResponse>> updateItem(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long itemId,
        @Valid @RequestBody ItemUpdateRequest request
    ) {
        ItemUpdateResponse response =
            sellerItemService.updateItem(authUser.userId(), itemId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 재고 입고
    @Operation(summary = "재고 입고")
    @PostMapping("/{itemId}/inbound")
    public ResponseEntity<ApiResponse<InboundResponse>> inbound(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long itemId,
        @Valid @RequestBody InboundRequest request
    ) {
        InboundResponse response =
            sellerItemService.inbound(authUser.userId(), itemId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
