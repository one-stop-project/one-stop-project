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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
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

        // 장바구니 담기 가능 여부 검증
        validateAddableProductItem(
            userId,
            productItem,
            request.quantity()
        );

        // 장바구니 조회 (없으면 생성)
        Cart cart = cartRepository.findByUserId(userId)
            .orElseGet(() -> cartRepository.save(new Cart(user)));

        // 동일 상품 옵션 존재 여부 확인
        CartItem cartItem = cartItemRepository.findByCartIdAndProductItemId(
                cart.getId(),
                productItem.getId()
            )
            .orElse(null);

        // 새 상품 옵션 추가 시 장바구니 최대 50종 검증
        if (cartItem == null) {
            validateCartItemLimit(cart.getId());
        }

        // 이미 존재하면 수량 증가
        if (cartItem != null) {

            int nextQuantity =
                cartItem.getQuantity() + request.quantity();

            // 재고 초과 검증
            validateStockLimit(
                productItem,
                nextQuantity
            );

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
     * - 장바구니가 없으면 빈 장바구니 응답 반환
     */
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {

        return cartRepository.findByUserId(userId)
            .map(cart -> {
                List<CartItem> cartItems = cartItemRepository.findAllByCartId(
                    cart.getId()
                );

                return CartResponse.of(
                    cart,
                    cartItems
                );
            })
            .orElseGet(CartResponse::empty);
    }

    /**
     * 장바구니 수량 변경
     * - 요청 quantity는 변경 후 최종 수량
     * - PATCH는 1~99 사이 수량 변경만 담당
     * - 삭제는 DELETE API에서 처리
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

            ProductItem productItem = cartItem.getProductItem();

            validateIncreaseCartItemQuantity(
                productItem,
                requestQuantity
            );

            cartItem.increaseQuantity(
                requestQuantity - currentQuantity
            );
        }

        // 감소
        else if (requestQuantity < currentQuantity) {

            cartItem.decreaseQuantity(
                currentQuantity - requestQuantity
            );
        }

        // 요청 수량이 현재 수량과 같으면 별도 변경 없이 현재 상태 반환
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

    /**
     * 장바구니 담기 가능 여부 검증
     * - STOP 상품 담기 불가
     * - 품절/재고 초과 상품 담기 불가
     * - 본인 상품 담기 불가
     */
    private void validateAddableProductItem(
        Long userId,
        ProductItem productItem,
        Integer quantity
    ) {

        if (!productItem.isOnSale()) {
            throw new CustomException(ErrorCode.CART_001);
        }

        validateStockLimit(
            productItem,
            quantity
        );

        if (productItem.getProduct()
            .getSeller()
            .getUser()
            .getId()
            .equals(userId)) {

            throw new CustomException(ErrorCode.CART_005);
        }
    }

    /**
     * 장바구니 최대 상품 종류 수 검증
     */
    private void validateCartItemLimit(Long cartId) {

        long cartItemCount = cartItemRepository.countByCartId(cartId);

        if (cartItemCount >= 50) {
            throw new CustomException(ErrorCode.CART_003);
        }
    }

    /**
     * 요청 수량 또는 변경 후 수량이 상품 재고를 초과하는지 검증
     */
    private void validateStockLimit(
        ProductItem productItem,
        int quantity
    ) {

        if (quantity > productItem.getStock()) {
            throw new CustomException(ErrorCode.INVENTORY_001);
        }
    }

    /**
     * 장바구니 수량 증가 가능 여부 검증
     * - nextQuantity는 변경 후 최종 수량
     * - STOP 상품은 수량 증가 불가
     * - 변경 후 최종 수량은 재고를 초과할 수 없음
     */
    private void validateIncreaseCartItemQuantity(
        ProductItem productItem,
        int nextQuantity
    ) {
        if (!productItem.isOnSale()) {
            throw new CustomException(ErrorCode.CART_001);
        }

        validateStockLimit(
            productItem,
            nextQuantity
        );
    }

}
