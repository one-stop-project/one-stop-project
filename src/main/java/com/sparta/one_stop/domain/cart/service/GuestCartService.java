package com.sparta.one_stop.domain.cart.service;

import com.sparta.one_stop.domain.cart.dto.request.AddCartItemRequest;
import com.sparta.one_stop.domain.cart.dto.request.UpdateCartItemRequest;
import com.sparta.one_stop.domain.cart.dto.response.CartItemDetailResponse;
import com.sparta.one_stop.domain.cart.dto.response.CartItemResponse;
import com.sparta.one_stop.domain.cart.dto.response.CartPageResponse;
import com.sparta.one_stop.domain.cart.dto.response.UpdateCartItemResponse;
import com.sparta.one_stop.domain.cart.support.GuestCartCookieProvider;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductItemRepository;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuestCartService {

    private static final String GUEST_CART_KEY_PREFIX = "guest:cart:";
    private static final Duration GUEST_CART_TTL = Duration.ofDays(7);
    private static final int MAX_CART_ITEM_COUNT = 50;
    private static final int MAX_CART_ITEM_QUANTITY = 99;

    private final RedisTemplate<String, String> redisTemplate;
    private final ProductItemRepository productItemRepository;
    private final GuestCartCookieProvider guestCartCookieProvider;

    /**
     * 비로그인 장바구니 담기
     * - Redis Hash itemId → quantity 구조 사용
     * - 동일 itemId 존재 시 수량 증가
     * - 조회/담기/수정/삭제 시 Redis TTL과 쿠키 만료 시간 갱신
     */
    @Transactional
    public CartItemResponse addCartItem(
        String guestCartId,
        HttpServletResponse response,
        AddCartItemRequest request
    ) {
        String resolvedGuestCartId = guestCartCookieProvider.resolveOrCreate(
            guestCartId,
            response
        );

        String redisKey = buildGuestCartKey(resolvedGuestCartId);
        String itemIdField = request.itemId().toString();

        ProductItem productItem = productItemRepository.findById(request.itemId())
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        validateAddableProductItem(
            productItem,
            request.quantity()
        );

        Object currentQuantityValue = redisTemplate.opsForHash()
            .get(redisKey, itemIdField);

        if (currentQuantityValue == null) {
            validateCartItemLimit(redisKey);

            redisTemplate.opsForHash().put(
                redisKey,
                itemIdField,
                request.quantity().toString()
            );

            refreshExpiration(
                redisKey,
                resolvedGuestCartId,
                response
            );

            return CartItemResponse.ofGuest(
                productItem.getId(),
                request.quantity()
            );
        }

        int currentQuantity = Integer.parseInt(currentQuantityValue.toString());
        int nextQuantity = currentQuantity + request.quantity();

        validateQuantityLimit(
            productItem,
            nextQuantity
        );

        redisTemplate.opsForHash().put(
            redisKey,
            itemIdField,
            String.valueOf(nextQuantity)
        );

        refreshExpiration(
            redisKey,
            resolvedGuestCartId,
            response
        );

        return CartItemResponse.ofGuest(
            productItem.getId(),
            nextQuantity
        );
    }

    /**
     * 비로그인 장바구니 조회
     * - Redis Hash에 저장된 itemId/quantity를 기반으로 상품 정보를 조회하여 응답 생성
     * - Redis Hash는 담은 순서를 보장하지 않으므로 itemId DESC 기준으로 정렬
     * - totalPrice/itemCount는 전체 비로그인 장바구니 기준으로 계산
     * - 조회 시 Redis TTL과 guest_cart_id 쿠키 만료 시간을 갱신
     */
    public CartPageResponse getCart(
        String guestCartId,
        HttpServletResponse response,
        Pageable pageable
    ) {
        String resolvedGuestCartId = guestCartCookieProvider.resolveOrCreate(
            guestCartId,
            response
        );

        String redisKey = buildGuestCartKey(resolvedGuestCartId);

        // 빈 장바구니 조회 시 Redis key가 없을 수 있음
        // 쿠키는 갱신하고, Redis TTL은 key가 존재하는 경우에만 적용됨
        Map<Object, Object> entries = redisTemplate.opsForHash()
            .entries(redisKey);

        refreshExpiration(
            redisKey,
            resolvedGuestCartId,
            response
        );

        if (entries.isEmpty()) {
            return CartPageResponse.empty(
                pageable.getPageNumber(),
                pageable.getPageSize()
            );
        }

        Map<Long, Integer> quantityMap = entries.entrySet()
            .stream()
            .collect(Collectors.toMap(
                entry -> Long.valueOf(entry.getKey().toString()),
                entry -> Integer.valueOf(entry.getValue().toString())
            ));

        List<Long> itemIds = quantityMap.keySet()
            .stream()
            .toList();

        if (itemIds.isEmpty()) {
            return CartPageResponse.empty(
                pageable.getPageNumber(),
                pageable.getPageSize()
            );
        }

        // Redis에는 남아 있지만 DB에서 조회되지 않는 상품 옵션은 응답에서 제외됨
        // 추후 필요 시 조회 실패 itemId를 Redis에서 정리하는 로직 추가 가능
        List<ProductItem> productItems = productItemRepository.findAllByIdInWithProduct(
            itemIds
        );

        // Redis Hash는 담은 순서를 보장하지 않으므로 itemId DESC 기준으로 정렬
        // 정확한 담기 순서가 필요하면 List/ZSet 등 별도 순서 저장 구조 필요
        List<CartItemDetailResponse> allContent = productItems.stream()
            .sorted(Comparator.comparing(ProductItem::getId).reversed())
            .map(productItem -> CartItemDetailResponse.ofGuest(
                productItem,
                quantityMap.get(productItem.getId())
            ))
            .toList();

        long totalPrice = allContent.stream()
            .mapToLong(item -> item.price() * item.quantity())
            .sum();

        int itemCount = allContent.stream()
            .mapToInt(CartItemDetailResponse::quantity)
            .sum();

        List<CartItemDetailResponse> pageContent = applyPaging(
            allContent,
            pageable
        );

        int totalElements = allContent.size();
        int totalPages = calculateTotalPages(
            totalElements,
            pageable.getPageSize()
        );

        return CartPageResponse.of(
            null,
            pageContent,
            totalPrice,
            itemCount,
            pageable.getPageNumber(),
            pageable.getPageSize(),
            totalElements,
            totalPages
        );
    }

    /**
     * 비로그인 장바구니 수량 변경
     * - 요청 quantity는 변경 후 최종 수량
     */
    @Transactional
    public UpdateCartItemResponse updateCartItemQuantity(
        String guestCartId,
        HttpServletResponse response,
        Long itemId,
        UpdateCartItemRequest request
    ) {
        String resolvedGuestCartId = guestCartCookieProvider.resolveOrCreate(
            guestCartId,
            response
        );

        String redisKey = buildGuestCartKey(resolvedGuestCartId);
        String itemIdField = itemId.toString();

        boolean exists = redisTemplate.opsForHash()
            .hasKey(redisKey, itemIdField);

        if (!exists) {
            throw new CustomException(ErrorCode.CART_004);
        }

        ProductItem productItem = productItemRepository.findById(itemId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        validateQuantityLimit(
            productItem,
            request.quantity()
        );

        redisTemplate.opsForHash().put(
            redisKey,
            itemIdField,
            request.quantity().toString()
        );

        refreshExpiration(
            redisKey,
            resolvedGuestCartId,
            response
        );

        return UpdateCartItemResponse.of(
            itemId,
            request.quantity()
        );
    }

    /**
     * 비로그인 장바구니 삭제
     */
    @Transactional
    public void deleteCartItem(
        String guestCartId,
        HttpServletResponse response,
        Long itemId
    ) {
        String resolvedGuestCartId = guestCartCookieProvider.resolveOrCreate(
            guestCartId,
            response
        );

        String redisKey = buildGuestCartKey(resolvedGuestCartId);
        String itemIdField = itemId.toString();

        boolean exists = redisTemplate.opsForHash()
            .hasKey(redisKey, itemIdField);

        if (!exists) {
            throw new CustomException(ErrorCode.CART_004);
        }

        redisTemplate.opsForHash().delete(
            redisKey,
            itemIdField
        );

        long remainingCount = redisTemplate.opsForHash()
            .size(redisKey);

        if (remainingCount <= 0) {
            redisTemplate.delete(redisKey);

            // 마지막 상품 삭제 시 Redis key는 제거하되 guest_cart_id 쿠키는 유지
            // 이후 비로그인 사용자가 다시 장바구니를 사용할 때 동일 식별자를 재사용
            guestCartCookieProvider.refreshCookie(
                resolvedGuestCartId,
                response
            );
            return;
        }

        refreshExpiration(
            redisKey,
            resolvedGuestCartId,
            response
        );
    }

    private String buildGuestCartKey(String guestCartId) {
        return GUEST_CART_KEY_PREFIX + guestCartId;
    }

    /**
     * 비로그인 장바구니 만료 시간 갱신
     * - Redis key가 존재하면 TTL을 7일로 갱신
     * - guest_cart_id 쿠키도 함께 갱신하여 Redis TTL과 쿠키 만료 시간을 맞춤
     */
    private void refreshExpiration(
        String redisKey,
        String guestCartId,
        HttpServletResponse response
    ) {
        boolean hasKey = redisTemplate.hasKey(redisKey);

        if (hasKey) {
            redisTemplate.expire(
                redisKey,
                GUEST_CART_TTL
            );
        }

        guestCartCookieProvider.refreshCookie(
            guestCartId,
            response
        );
    }

    /**
     * 비로그인 장바구니 상품 종류 수 검증
     * - Redis Hash field 개수를 기준으로 최대 50종 제한
     */
    private void validateCartItemLimit(String redisKey) {
        long cartItemCount = redisTemplate.opsForHash()
            .size(redisKey);

        if (cartItemCount >= MAX_CART_ITEM_COUNT) {
            throw new CustomException(ErrorCode.CART_003);
        }
    }

    /**
     * 비로그인 장바구니 담기 가능 여부 검증
     * - 판매 중지 상품은 담기 불가
     * - 수량은 1~99개까지만 허용
     * - 현재 상품 재고를 초과할 수 없음
     */
    private void validateAddableProductItem(
        ProductItem productItem,
        int quantity
    ) {
        if (!productItem.isOnSale()) {
            throw new CustomException(ErrorCode.CART_001);
        }

        validateQuantityLimit(
            productItem,
            quantity
        );
    }

    /**
     * 비로그인 장바구니 수량 검증
     * - 수량은 1~99개까지만 허용
     * - 현재 상품 재고를 초과할 수 없음
     */
    private void validateQuantityLimit(
        ProductItem productItem,
        int quantity
    ) {
        if (quantity <= 0 || quantity > MAX_CART_ITEM_QUANTITY) {
            throw new CustomException(ErrorCode.CART_002);
        }

        if (quantity > productItem.getStock()) {
            throw new CustomException(ErrorCode.INVENTORY_001);
        }
    }

    /**
     * Redis Hash 조회 결과를 메모리에서 페이징 처리
     * - 비로그인 장바구니 최대 50종 제한이 있으므로 메모리 페이징으로 처리
     */
    private List<CartItemDetailResponse> applyPaging(
        List<CartItemDetailResponse> content,
        Pageable pageable
    ) {
        int start = (int) pageable.getOffset();

        if (start >= content.size()) {
            return List.of();
        }

        int end = Math.min(
            start + pageable.getPageSize(),
            content.size()
        );

        return content.subList(
            start,
            end
        );
    }

    /**
     * 전체 상품 종류 수와 page size를 기준으로 전체 페이지 수 계산
     */
    private int calculateTotalPages(
        int totalElements,
        int size
    ) {
        if (totalElements == 0) {
            return 0;
        }

        return (int) Math.ceil((double) totalElements / size);
    }

}
