package com.sparta.one_stop.domain.cart.service;

import com.sparta.one_stop.domain.cart.dto.request.AddCartItemRequest;
import com.sparta.one_stop.domain.cart.dto.request.UpdateCartItemRequest;
import com.sparta.one_stop.domain.cart.dto.response.CartItemDetailResponse;
import com.sparta.one_stop.domain.cart.dto.response.CartItemResponse;
import com.sparta.one_stop.domain.cart.dto.response.CartPageResponse;
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
import com.sparta.one_stop.global.security.AuthUser;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CartService {

    private static final int MAX_CART_ITEM_QUANTITY = 99;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductItemRepository productItemRepository;
    private final GuestCartService guestCartService;

    /**
     * 장바구니 담기
     * - 로그인 사용자는 DB 장바구니 사용
     * - 비로그인 사용자는 Redis 장바구니 사용
     */
    public CartItemResponse addCartItem(
        AuthUser authUser,
        String guestCartId,
        HttpServletResponse response,
        AddCartItemRequest request
    ) {
        if (authUser != null) {
            return addCartItem(
                authUser.userId(),
                request
            );
        }

        return guestCartService.addCartItem(
            guestCartId,
            response,
            request
        );
    }

    /**
     * 장바구니 조회
     * - 로그인 사용자는 DB 장바구니 조회
     * - 비로그인 사용자는 Redis 장바구니 조회
     */
    @Transactional(readOnly = true)
    public CartPageResponse getCart(
        AuthUser authUser,
        String guestCartId,
        HttpServletResponse response,
        Pageable pageable
    ) {
        if (authUser != null) {
            return getCart(
                authUser.userId(),
                pageable
            );
        }

        return guestCartService.getCart(
            guestCartId,
            response,
            pageable
        );
    }

    /**
     * 장바구니 수량 변경
     * - PATCH 경로 변수는 itemId 기준
     */
    public UpdateCartItemResponse updateCartItemQuantity(
        AuthUser authUser,
        String guestCartId,
        HttpServletResponse response,
        Long itemId,
        UpdateCartItemRequest request
    ) {
        if (authUser != null) {
            return updateCartItemQuantityByItemId(
                authUser.userId(),
                itemId,
                request
            );
        }

        return guestCartService.updateCartItemQuantity(
            guestCartId,
            response,
            itemId,
            request
        );
    }

    /**
     * 장바구니 삭제
     * - DELETE 경로 변수는 itemId 기준
     */
    public void deleteCartItem(
        AuthUser authUser,
        String guestCartId,
        HttpServletResponse response,
        Long itemId
    ) {
        if (authUser != null) {
            deleteCartItemByItemId(
                authUser.userId(),
                itemId
            );
            return;
        }

        guestCartService.deleteCartItem(
            guestCartId,
            response,
            itemId
        );
    }


    /**
     * 로그인 장바구니 담기
     * - DB 장바구니가 없으면 생성
     * - 동일 상품 옵션이 이미 존재하면 수량 증가
     * - 새 상품 옵션 추가 시 최대 50종 제한 검증
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

            // 장바구니 수량 상한 검증
            validateCartItemQuantityLimit(nextQuantity);

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
     * 로그인 장바구니 조회
     * - DB 장바구니가 없으면 빈 장바구니 응답 반환
     * - 장바구니 상품은 페이징 조회
     * - 상품 옵션/상품 정보를 함께 조회하여 DTO 변환 시 N+1 방지
     * - totalPrice/itemCount는 전체 장바구니 기준으로 계산
     */
    @Transactional(readOnly = true)
    public CartPageResponse getCart(
        Long userId,
        Pageable pageable
    ) {

        return cartRepository.findByUserId(userId)
            .map(cart -> {
                Page<CartItem> cartItemPage =
                    cartItemRepository.findPageByCartIdWithProduct(
                        cart.getId(),
                        pageable
                    );

                Long totalPrice = cartItemRepository.sumTotalPriceByCartId(
                    cart.getId()
                );

                Long totalQuantity = cartItemRepository.sumQuantityByCartId(
                    cart.getId()
                );

                List<CartItemDetailResponse> content =
                    cartItemPage.getContent()
                        .stream()
                        .map(CartItemDetailResponse::of)
                        .toList();

                return CartPageResponse.of(
                    cart.getId(),
                    content,
                    totalPrice,
                    totalQuantity.intValue(),
                    cartItemPage.getNumber(),
                    cartItemPage.getSize(),
                    cartItemPage.getTotalElements(),
                    cartItemPage.getTotalPages()
                );
            })
            .orElseGet(() -> CartPageResponse.empty(
                pageable.getPageNumber(),
                pageable.getPageSize()
            ));
    }

    /**
     * 로그인 장바구니 수량 변경
     * - PATCH /api/carts/items/{itemId} 요청을 처리
     * - userId로 장바구니를 조회한 뒤 cartId + itemId 기준으로 CartItem 조회
     * - cartItemId가 아닌 상품 옵션 ID(itemId)를 기준으로 수정
     */
    public UpdateCartItemResponse updateCartItemQuantityByItemId(
        Long userId,
        Long itemId,
        UpdateCartItemRequest request
    ) {
        Cart cart = cartRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.CART_004));

        CartItem cartItem = cartItemRepository.findByCartIdAndProductItemId(
                cart.getId(),
                itemId
            )
            .orElseThrow(() -> new CustomException(ErrorCode.CART_004));

        int currentQuantity = cartItem.getQuantity();
        int requestQuantity = request.quantity();

        if (requestQuantity > currentQuantity) {
            ProductItem productItem = cartItem.getProductItem();

            validateIncreaseCartItemQuantity(
                productItem,
                requestQuantity
            );

            cartItem.increaseQuantity(
                requestQuantity - currentQuantity
            );
        } else if (requestQuantity < currentQuantity) {
            cartItem.decreaseQuantity(
                currentQuantity - requestQuantity
            );
        }

        return UpdateCartItemResponse.of(
            itemId,
            requestQuantity
        );
    }

    /**
     * 로그인 장바구니 상품 삭제
     * - DELETE /api/carts/items/{itemId} 요청을 처리
     * - userId로 장바구니를 조회한 뒤 cartId + itemId 기준으로 CartItem 조회
     * - cartItemId가 아닌 상품 옵션 ID(itemId)를 기준으로 삭제
     */
    public void deleteCartItemByItemId(
        Long userId,
        Long itemId
    ) {
        Cart cart = cartRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.CART_004));

        CartItem cartItem = cartItemRepository.findByCartIdAndProductItemId(
                cart.getId(),
                itemId
            )
            .orElseThrow(() -> new CustomException(ErrorCode.CART_004));

        cartItemRepository.delete(cartItem);
    }

    /**
     * 장바구니 담기 가능 여부 검증
     * 검증 우선순위:
     * 1. 본인 상품 담기 불가
     * 2. STOP 상품 담기 불가
     * 3. 장바구니 수량 상한 검증
     * 4. 품절/재고 초과 상품 담기 불가
     */
    private void validateAddableProductItem(
        Long userId,
        ProductItem productItem,
        Integer quantity
    ) {

        // 1. 본인 상품 먼저
        if (productItem.getProduct()
            .getSeller()
            .getUser()
            .getId()
            .equals(userId)) {

            throw new CustomException(ErrorCode.CART_005);
        }

        // 2. STOP
        if (!productItem.isOnSale()) {
            throw new CustomException(ErrorCode.CART_001);
        }

        // 3. 장바구니 수량 범위
        validateCartItemQuantityLimit(quantity);

        // 4. 재고
        validateStockLimit(
            productItem,
            quantity
        );
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
     * 장바구니 상품 수량 최대 99개 검증
     */
    private void validateCartItemQuantityLimit(int quantity) {
        if (quantity > MAX_CART_ITEM_QUANTITY) {
            throw new CustomException(ErrorCode.CART_002);
        }
    }

    /**
     * 장바구니 수량 증가 가능 여부 검증
     * - nextQuantity는 변경 후 최종 수량
     * - STOP 상품은 수량 증가 불가
     * - 변경 후 최종 수량은 99개를 초과할 수 없음
     * - 변경 후 최종 수량은 재고를 초과할 수 없음
     */
    private void validateIncreaseCartItemQuantity(
        ProductItem productItem,
        int nextQuantity
    ) {
        if (!productItem.isOnSale()) {
            throw new CustomException(ErrorCode.CART_001);
        }

        validateCartItemQuantityLimit(nextQuantity);

        validateStockLimit(
            productItem,
            nextQuantity
        );
    }

}
