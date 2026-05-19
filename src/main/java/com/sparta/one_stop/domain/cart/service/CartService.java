package com.sparta.one_stop.domain.cart.service;

import com.sparta.one_stop.domain.cart.dto.request.AddCartItemRequest;
import com.sparta.one_stop.domain.cart.dto.request.UpdateCartItemRequest;
import com.sparta.one_stop.domain.cart.dto.response.CartItemResponse;
import com.sparta.one_stop.domain.cart.dto.response.CartResponse;
import com.sparta.one_stop.domain.cart.dto.response.UpdateCartItemResponse;
import com.sparta.one_stop.domain.cart.entity.Cart;
import com.sparta.one_stop.domain.cart.entity.CartItem;
import com.sparta.one_stop.domain.cart.repository.CartItemRepository;
import com.sparta.one_stop.domain.cart.repository.CartRepository;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductItemRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    private final UserRepository userRepository;
    private final ProductItemRepository productItemRepository;

    /**
     * 장바구니 담기
     * - cart 없으면 생성
     * - 동일 상품 옵션 존재 시 수량 증가
     */
    public CartItemResponse addCartItem(
        Long userId,
        AddCartItemRequest request
    ) {

        // 유저 조회
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));

        // 상품 옵션 조회
        ProductItem productItem = productItemRepository.findById(
                request.itemId()
            )
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        // 장바구니 조회 (없으면 생성)
        Cart cart = cartRepository.findByUserId(userId)
            .orElseGet(() -> cartRepository.save(new Cart(user)));

        // 동일 상품 옵션 존재 여부 확인
        CartItem cartItem = cartItemRepository.findByCartIdAndProductItemId(
                cart.getId(),
                productItem.getId()
            )
            .orElse(null);

        // 이미 존재하면 수량 증가
        if (cartItem != null) {

            cartItem.increaseQuantity(request.quantity());

            return CartItemResponse.of(cartItem);
        }

        // 새 장바구니 상품 생성
        CartItem newCartItem = new CartItem(
            cart,
            productItem,
            request.quantity()
        );

        cartItemRepository.save(newCartItem);

        return CartItemResponse.of(newCartItem);
    }

    /**
     * 장바구니 조회
     */
    @Transactional
    public CartResponse getCart(Long userId) {

        Cart cart = cartRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.CART_003));

        List<CartItem> cartItems = cartItemRepository.findAllByCartId(
            cart.getId()
        );

        return CartResponse.of(cart, cartItems);
    }

    /**
     * 장바구니 수량 변경
     */
    public UpdateCartItemResponse updateCartItemQuantity(
        Long userId,
        Long cartItemId,
        UpdateCartItemRequest request
    ) {

        CartItem cartItem = cartItemRepository.findById(cartItemId)
            .orElseThrow(() -> new CustomException(ErrorCode.CART_004));

        // 본인 장바구니 검증
        validateCartOwner(userId, cartItem);

        int currentQuantity = cartItem.getQuantity();
        int requestQuantity = request.quantity();

        // 증가
        if (requestQuantity > currentQuantity) {

            cartItem.increaseQuantity(
                requestQuantity - currentQuantity
            );
        }

        // 감소
        else if (requestQuantity < currentQuantity) {

            boolean shouldDelete = cartItem.decreaseQuantity(
                currentQuantity - requestQuantity
            );

            // 수량 0이면 삭제
            if (shouldDelete) {
                cartItemRepository.delete(cartItem);
            }
        }

        return UpdateCartItemResponse.of(
            cartItemId,
            requestQuantity
        );
    }

    /**
     * 장바구니 삭제
     */
    public void deleteCartItem(
        Long userId,
        Long cartItemId
    ) {

        CartItem cartItem = cartItemRepository.findById(cartItemId)
            .orElseThrow(() -> new CustomException(ErrorCode.CART_004));

        // 본인 장바구니 검증
        validateCartOwner(userId, cartItem);

        cartItemRepository.delete(cartItem);
    }

    /**
     * 장바구니 소유자 검증
     */
    private void validateCartOwner(
        Long userId,
        CartItem cartItem
    ) {

        if (!cartItem.getCart()
            .getUser()
            .getId()
            .equals(userId)) {

            throw new CustomException(ErrorCode.CART_006);
        }
    }

}
