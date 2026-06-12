package com.sparta.one_stop.domain.cart.service;

import com.sparta.one_stop.domain.cart.dto.request.AddCartItemRequest;
import com.sparta.one_stop.domain.cart.dto.request.UpdateCartItemRequest;
import com.sparta.one_stop.domain.cart.dto.response.CartItemDetailResponse;
import com.sparta.one_stop.domain.cart.dto.response.CartItemResponse;
import com.sparta.one_stop.domain.cart.dto.response.CartPageResponse;
import com.sparta.one_stop.domain.cart.dto.response.UpdateCartItemResponse;
import com.sparta.one_stop.domain.cart.support.GuestCartCookieProvider;
import com.sparta.one_stop.domain.cart.support.GuestCartRedisKeyProvider;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductItemRepository;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuestCartService {

    private static final Duration GUEST_CART_TTL = Duration.ofDays(7);
    private static final int MAX_CART_ITEM_COUNT = 50;
    private static final int MAX_CART_ITEM_QUANTITY = 99;

    private final RedisTemplate<String, String> redisTemplate;
    private final ProductItemRepository productItemRepository;
    private final GuestCartCookieProvider guestCartCookieProvider;
    private final GuestCartRedisKeyProvider guestCartRedisKeyProvider;

    /**
     * 비로그인 장바구니 담기
     * - Redis Hash는 itemId → quantity 수량 저장용으로 사용
     * - Redis ZSet은 itemId → 최초 담기 timestamp 순서 저장용으로 사용
     * - 동일 itemId 존재 시 Hash quantity만 증가
     * - 동일 itemId 재담기 시 ZSet score는 변경하지 않아 최초 담기 순서를 유지
     * - 조회/담기/수정/삭제 시 Redis Hash/ZSet TTL과 쿠키 만료 시간 갱신
     */
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
        String orderKey = buildGuestCartOrderKey(resolvedGuestCartId);
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

            // 신규 상품은 최초 담기 순서를 ZSet에 저장
            redisTemplate.opsForZSet().add(
                orderKey,
                itemIdField,
                System.currentTimeMillis()
            );

            refreshExpiration(
                redisKey,
                orderKey,
                resolvedGuestCartId,
                response
            );

            return CartItemResponse.ofGuest(
                productItem.getId(),
                request.quantity()
            );
        }

        // 기존 상품 재담기
        // 원칙적으로 ZSet score는 변경하지 않지만,
        // 2차 구현에서 생성된 Hash-only 데이터 호환을 위해 score가 없으면 추가
        Double score = redisTemplate.opsForZSet()
            .score(orderKey, itemIdField);

        if (score == null) {
            redisTemplate.opsForZSet().add(
                orderKey,
                itemIdField,
                System.currentTimeMillis()
            );
        }

        Integer currentQuantity = parseQuantityOrNull(
            itemIdField,
            currentQuantityValue
        );

        // Redis에 저장된 기존 수량이 오염된 경우 기존 수량을 0으로 간주하고 요청 수량부터 다시 계산한다.
        // add 요청 자체는 유효성 검증을 통과한 수량이므로 기존 오염 데이터 때문에 500이 발생하지 않도록 방어한다.
        int nextQuantity = (currentQuantity == null ? 0 : currentQuantity) + request.quantity();

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
            orderKey,
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
     * - Redis ZSet에 저장된 담기 순서를 기준으로 조회 순서를 정렬
     * - ZSet 데이터가 없는 과거 Hash 데이터는 itemId DESC 기준으로 fallback 처리
     * - totalPrice/itemCount는 전체 비로그인 장바구니 기준으로 계산
     * - 조회 시 Redis Hash/ZSet TTL과 guest_cart_id 쿠키 만료 시간을 갱신
     */
    @Transactional(readOnly = true)
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
        String orderKey = buildGuestCartOrderKey(resolvedGuestCartId);

        // 빈 장바구니 조회 시 Redis key가 없을 수 있음
        // 쿠키는 갱신하고, Redis TTL은 key가 존재하는 경우에만 적용됨
        Map<Object, Object> entries = redisTemplate.opsForHash()
            .entries(redisKey);

        refreshExpiration(
            redisKey,
            orderKey,
            resolvedGuestCartId,
            response
        );

        if (entries.isEmpty()) {
            return CartPageResponse.empty(
                pageable.getPageNumber(),
                pageable.getPageSize()
            );
        }

        Map<Long, Integer> quantityMap = buildValidQuantityMap(entries);

        if (quantityMap.isEmpty()) {
            return CartPageResponse.empty(
                pageable.getPageNumber(),
                pageable.getPageSize()
            );
        }

        List<Long> orderedItemIds = getOrderedItemIds(
            orderKey,
            quantityMap
        );

        if (orderedItemIds.isEmpty()) {
            return CartPageResponse.empty(
                pageable.getPageNumber(),
                pageable.getPageSize()
            );
        }

        // Redis에는 남아 있지만 DB에서 조회되지 않는 상품 옵션은 응답에서 제외됨
        // 추후 필요 시 조회 실패 itemId를 Redis에서 정리하는 로직 추가 가능
        List<ProductItem> productItems = productItemRepository.findAllByIdInWithProduct(
            orderedItemIds
        );

        Map<Long, ProductItem> productItemMap = productItems.stream()
            .collect(Collectors.toMap(
                ProductItem::getId,
                Function.identity()
            ));

        // Redis ZSet 담기 순서 기준으로 응답 생성
        List<CartItemDetailResponse> allContent = orderedItemIds.stream()
            .map(productItemMap::get)
            .filter(productItem -> productItem != null)
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
     * Redis Hash entries에서 유효한 itemId/quantity만 추출한다.
     * itemId 또는 quantity 파싱에 실패한 항목은 오염된 Redis 데이터로 보고 조회 대상에서 제외한다.
     */
    private Map<Long, Integer> buildValidQuantityMap(
        Map<Object, Object> entries
    ) {
        Map<Long, Integer> quantityMap = new HashMap<>();

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String itemIdField = entry.getKey().toString();

            Long itemId = parseItemIdOrNull(itemIdField);
            if (itemId == null) {
                continue;
            }

            Integer quantity = parseQuantityOrNull(
                itemIdField,
                entry.getValue()
            );
            if (quantity == null) {
                continue;
            }

            quantityMap.put(
                itemId,
                quantity
            );
        }

        return quantityMap;
    }

    /**
     * 비로그인 장바구니 수량 변경
     * - 요청 quantity는 변경 후 최종 수량
     */
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
        String orderKey = buildGuestCartOrderKey(resolvedGuestCartId);
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
            orderKey,
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
     * - Redis Hash에서 itemId field 제거
     * - Redis ZSet order key에서도 itemId member 제거
     * - 마지막 상품 삭제 시 Hash key와 ZSet key를 모두 삭제
     * - guest_cart_id 쿠키는 유지하여 이후 비로그인 장바구니 사용 시 동일 식별자를 재사용
     */
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
        String orderKey = buildGuestCartOrderKey(resolvedGuestCartId);
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

        redisTemplate.opsForZSet().remove(
            orderKey,
            itemIdField
        );

        long remainingCount = redisTemplate.opsForHash()
            .size(redisKey);

        if (remainingCount <= 0) {
            redisTemplate.delete(redisKey);
            redisTemplate.delete(orderKey);

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
            orderKey,
            resolvedGuestCartId,
            response
        );
    }

    private String buildGuestCartKey(String guestCartId) {
        if (guestCartId == null || guestCartId.isBlank()) {
            throw new IllegalArgumentException("guestCartId는 필수입니다.");
        }

        return guestCartRedisKeyProvider.buildGuestCartKey(guestCartId);
    }

    private String buildGuestCartOrderKey(String guestCartId) {
        if (guestCartId == null || guestCartId.isBlank()) {
            throw new IllegalArgumentException("guestCartId는 필수입니다.");
        }

        return guestCartRedisKeyProvider.buildGuestCartOrderKey(guestCartId);
    }

    /**
     * 비로그인 장바구니 만료 시간 갱신
     * - Redis Hash key가 존재하면 TTL을 7일로 갱신
     * - Redis ZSet order key가 존재하면 TTL을 7일로 갱신
     * - guest_cart_id 쿠키도 함께 갱신하여 Redis TTL과 쿠키 만료 시간을 맞춤
     */
    private void refreshExpiration(
        String redisKey,
        String orderKey,
        String guestCartId,
        HttpServletResponse response
    ) {
        boolean hasCartKey = redisTemplate.hasKey(redisKey);

        if (hasCartKey) {
            redisTemplate.expire(
                redisKey,
                GUEST_CART_TTL
            );
        }

        boolean hasOrderKey = redisTemplate.hasKey(orderKey);

        if (hasOrderKey) {
            redisTemplate.expire(
                orderKey,
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

    /**
     * 비로그인 장바구니 조회 순서 생성
     * - Redis ZSet order key가 있으면 ZSet score ASC 기준으로 먼저 조회
     * - ZSet에 없는 Hash-only 데이터는 itemId DESC 기준으로 뒤에 추가
     * - ZSet이 없는 과거 Hash 데이터는 itemId DESC 기준으로 fallback 처리
     */
    private List<Long> getOrderedItemIds(
        String orderKey,
        Map<Long, Integer> quantityMap
    ) {
        Set<String> orderedItemFields = redisTemplate.opsForZSet()
            .range(orderKey, 0, -1);

        if (orderedItemFields == null || orderedItemFields.isEmpty()) {
            return getFallbackOrderedItemIds(quantityMap);
        }

        List<Long> orderedItemIds = new ArrayList<>();

        for (String itemIdField : orderedItemFields) {
            Long itemId = parseItemIdOrNull(itemIdField);

            if (itemId != null && quantityMap.containsKey(itemId)) {
                orderedItemIds.add(itemId);
            }
        }

        // ZSet에 없는 Hash-only 데이터 fallback 처리
        // 2차 구현에서 생성된 기존 Hash 데이터가 남아있는 경우 뒤에 붙임
        quantityMap.keySet()
            .stream()
            .sorted(Comparator.reverseOrder())
            .filter(itemId -> !orderedItemIds.contains(itemId))
            .forEach(orderedItemIds::add);

        if (orderedItemIds.isEmpty()) {
            return getFallbackOrderedItemIds(quantityMap);
        }

        return orderedItemIds;
    }

    private List<Long> getFallbackOrderedItemIds(Map<Long, Integer> quantityMap) {
        return quantityMap.keySet()
            .stream()
            .sorted(Comparator.reverseOrder())
            .toList();
    }

    /**
     * Redis Hash field itemId 파싱
     * 파싱 실패 시 해당 항목은 조회/계산 대상에서 제외한다.
     */
    private Long parseItemIdOrNull(String itemIdField) {
        try {
            return Long.valueOf(itemIdField);
        } catch (NumberFormatException e) {
            log.warn(
                "Redis 장바구니 itemId 파싱 실패 - itemIdField: {}",
                itemIdField
            );
            return null;
        }
    }

    /**
     * Redis Hash value quantity 파싱
     * 수량은 1 이상 99 이하만 유효한 장바구니 수량으로 인정한다.
     * 파싱 실패 또는 범위 오류 시 해당 항목은 조회/계산 대상에서 제외한다.
     */
    private Integer parseQuantityOrNull(
        String itemIdField,
        Object quantityValue
    ) {
        if (quantityValue == null) {
            log.warn(
                "Redis 장바구니 수량 값 없음 - itemId: {}",
                itemIdField
            );
            return null;
        }

        try {
            int quantity = Integer.parseInt(quantityValue.toString());

            if (quantity <= 0 || quantity > MAX_CART_ITEM_QUANTITY) {
                log.warn(
                    "Redis 장바구니 수량 범위 오류 - itemId: {}, quantity: {}",
                    itemIdField,
                    quantity
                );
                return null;
            }

            return quantity;
        } catch (NumberFormatException e) {
            log.warn(
                "Redis 장바구니 수량 파싱 실패 - itemId: {}, value: {}",
                itemIdField,
                quantityValue
            );
            return null;
        }
    }

}
