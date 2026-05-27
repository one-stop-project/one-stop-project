package com.sparta.one_stop.domain.cart.service;

import com.sparta.one_stop.domain.cart.entity.Cart;
import com.sparta.one_stop.domain.cart.entity.CartItem;
import com.sparta.one_stop.domain.cart.repository.CartItemRepository;
import com.sparta.one_stop.domain.cart.repository.CartRepository;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductItemRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CartMergeExecutor {

    private static final int MAX_CART_ITEM_COUNT = 50;
    private static final int MAX_CART_ITEM_QUANTITY = 99;

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductItemRepository productItemRepository;

    /**
     * Redis 비로그인 장바구니 데이터를 DB 장바구니에 병합
     * - DB 작업만 담당
     * - Redis 삭제와 guest_cart_id 쿠키 삭제는 담당하지 않음
     * - 동일 itemId가 있으면 수량 합산
     * - 신규 상품은 DB 장바구니 50종 한도 내에서만 추가
     * - 최종 수량은 99개와 현재 재고를 초과하지 않도록 보정
     * - STOP / stock = 0 상품은 merge하지 않음
     */
    public void execute(
        Long userId,
        Map<Long, Integer> guestQuantityMap
    ) {

        if (guestQuantityMap == null || guestQuantityMap.isEmpty()) {
            return;
        }

        Cart cart = cartRepository.findByUserId(userId)
            .orElseGet(() -> {
                // 로그인 성공 후 전달된 userId이므로 존재가 보장된다고 보고 프록시 참조 사용
                User userRef = userRepository.getReferenceById(userId);
                return cartRepository.save(new Cart(userRef));
            });

        List<Long> itemIds = guestQuantityMap.keySet()
            .stream()
            .toList();

        List<ProductItem> productItems = productItemRepository.findAllByIdInWithProduct(
            itemIds
        );

        List<CartItem> existingCartItems = cartItemRepository.findAllByCartIdWithProductItem(
            cart.getId()
        );

        Map<Long, CartItem> existingItemMap = existingCartItems.stream()
            .collect(Collectors.toMap(
                cartItem -> cartItem.getProductItem().getId(),
                cartItem -> cartItem
            ));

        int currentCartItemCount = existingCartItems.size();

        for (ProductItem productItem : productItems) {
            if (!isMergeable(productItem)) {
                continue;
            }

            Integer guestQuantity = guestQuantityMap.get(productItem.getId());

            if (guestQuantity == null || guestQuantity <= 0) {
                continue;
            }

            CartItem existingCartItem = existingItemMap.get(productItem.getId());

            if (existingCartItem != null) {
                mergeExistingCartItem(
                    existingCartItem,
                    productItem,
                    guestQuantity
                );
                continue;
            }

            if (currentCartItemCount >= MAX_CART_ITEM_COUNT) {
                continue;
            }

            int finalQuantity = calculateFinalQuantity(
                guestQuantity,
                productItem
            );

            CartItem newCartItem = new CartItem(
                cart,
                productItem,
                finalQuantity
            );

            cartItemRepository.save(newCartItem);
            currentCartItemCount++;
        }
    }

    /**
     * merge 가능한 상품 옵션 여부
     * - 판매 중지 상품은 제외
     * - 재고가 0인 상품은 제외
     */
    private boolean isMergeable(ProductItem productItem) {
        return productItem.isOnSale()
            && productItem.getStock() > 0;
    }

    private void mergeExistingCartItem(
        CartItem cartItem,
        ProductItem productItem,
        int guestQuantity
    ) {
        int mergedQuantity = cartItem.getQuantity() + guestQuantity;

        int finalQuantity = Math.min(
            mergedQuantity,
            MAX_CART_ITEM_QUANTITY
        );

        finalQuantity = Math.min(
            finalQuantity,
            productItem.getStock().intValue()
        );

        cartItem.changeQuantity(finalQuantity);
    }

    private int calculateFinalQuantity(
        int guestQuantity,
        ProductItem productItem
    ) {
        int finalQuantity = Math.min(
            guestQuantity,
            MAX_CART_ITEM_QUANTITY
        );

        return Math.min(
            finalQuantity,
            productItem.getStock().intValue()
        );
    }

}
