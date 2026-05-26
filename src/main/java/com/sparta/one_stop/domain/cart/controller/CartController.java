package com.sparta.one_stop.domain.cart.controller;

import com.sparta.one_stop.domain.cart.dto.request.AddCartItemRequest;
import com.sparta.one_stop.domain.cart.dto.request.UpdateCartItemRequest;
import com.sparta.one_stop.domain.cart.dto.response.CartItemResponse;
import com.sparta.one_stop.domain.cart.dto.response.CartPageResponse;
import com.sparta.one_stop.domain.cart.dto.response.UpdateCartItemResponse;
import com.sparta.one_stop.domain.cart.service.CartService;
import com.sparta.one_stop.domain.cart.support.GuestCartCookieProvider;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
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
        @CookieValue(
            name = GuestCartCookieProvider.GUEST_CART_COOKIE_NAME,
            required = false
        ) String guestCartId,
        HttpServletResponse response,
        @Valid @RequestBody AddCartItemRequest request
    ) {
        CartItemResponse cartItemResponse = cartService.addCartItem(
            authUser,
            guestCartId,
            response,
            request
        );

        return ResponseEntity.ok(
            ApiResponse.success(cartItemResponse)
        );
    }

    // 장바구니 조회
    @GetMapping
    public ResponseEntity<ApiResponse<CartPageResponse>> getCart(
        @AuthenticationPrincipal AuthUser authUser,
        @CookieValue(
            name = GuestCartCookieProvider.GUEST_CART_COOKIE_NAME,
            required = false
        ) String guestCartId,
        HttpServletResponse response,
        @PageableDefault(
            size = 20,
            sort = "id",
            direction = Sort.Direction.DESC
        ) Pageable pageable
    ) {
        CartPageResponse cartResponse = cartService.getCart(
            authUser,
            guestCartId,
            response,
            pageable
        );

        return ResponseEntity.ok(
            ApiResponse.success(cartResponse)
        );
    }

    // 장바구니 수량 변경
    @PatchMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<UpdateCartItemResponse>> updateCartItemQuantity(
        @AuthenticationPrincipal AuthUser authUser,
        @CookieValue(
            name = GuestCartCookieProvider.GUEST_CART_COOKIE_NAME,
            required = false
        ) String guestCartId,
        HttpServletResponse response,
        @PathVariable Long itemId,
        @Valid @RequestBody UpdateCartItemRequest request
    ) {
        UpdateCartItemResponse updateResponse = cartService.updateCartItemQuantity(
            authUser,
            guestCartId,
            response,
            itemId,
            request
        );

        return ResponseEntity.ok(
            ApiResponse.success(updateResponse)
        );
    }

    // 장바구니 삭제
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteCartItem(
        @AuthenticationPrincipal AuthUser authUser,
        @CookieValue(
            name = GuestCartCookieProvider.GUEST_CART_COOKIE_NAME,
            required = false
        ) String guestCartId,
        HttpServletResponse response,
        @PathVariable Long itemId
    ) {
        cartService.deleteCartItem(
            authUser,
            guestCartId,
            response,
            itemId
        );

        return ResponseEntity.ok(
            ApiResponse.success()
        );
    }

}
