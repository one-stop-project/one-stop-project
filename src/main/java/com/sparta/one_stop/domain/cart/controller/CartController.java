package com.sparta.one_stop.domain.cart.controller;

import com.sparta.one_stop.domain.cart.dto.request.AddCartItemRequest;
import com.sparta.one_stop.domain.cart.dto.request.UpdateCartItemRequest;
import com.sparta.one_stop.domain.cart.dto.response.CartItemResponse;
import com.sparta.one_stop.domain.cart.dto.response.CartResponse;
import com.sparta.one_stop.domain.cart.dto.response.UpdateCartItemResponse;
import com.sparta.one_stop.domain.cart.service.CartService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/carts")
public class CartController {

    private final CartService cartService;

    // 장바구니 담기
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartItemResponse>> addCartItem(
        @AuthenticationPrincipal AuthUser authUser,
        @Valid @RequestBody AddCartItemRequest request
    ) {
        CartItemResponse response = cartService.addCartItem(
            authUser.userId(),
            request
        );

        return ResponseEntity.ok(
            ApiResponse.success(response)
        );
    }

    // 장바구니 조회
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
        @AuthenticationPrincipal AuthUser authUser
    ) {
        CartResponse response = cartService.getCart(
            authUser.userId()
        );

        return ResponseEntity.ok(
            ApiResponse.success(response)
        );
    }

    // 장바구니 수량 변경
    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<UpdateCartItemResponse>> updateCartItemQuantity(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long cartItemId,
        @Valid @RequestBody UpdateCartItemRequest request
    ) {
        UpdateCartItemResponse response = cartService.updateCartItemQuantity(
            authUser.userId(),
            cartItemId,
            request
        );

        return ResponseEntity.ok(
            ApiResponse.success(response)
        );
    }

    // 장바구니 삭제
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> deleteCartItem(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long cartItemId
    ) {
        cartService.deleteCartItem(
            authUser.userId(),
            cartItemId
        );

        return ResponseEntity.ok(
            ApiResponse.success()
        );
    }

}
