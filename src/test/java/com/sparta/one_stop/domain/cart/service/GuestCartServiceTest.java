package com.sparta.one_stop.domain.cart.service;

import com.sparta.one_stop.domain.cart.dto.request.AddCartItemRequest;
import com.sparta.one_stop.domain.cart.dto.request.UpdateCartItemRequest;
import com.sparta.one_stop.domain.cart.dto.response.CartItemDetailResponse;
import com.sparta.one_stop.domain.cart.dto.response.CartItemResponse;
import com.sparta.one_stop.domain.cart.dto.response.CartPageResponse;
import com.sparta.one_stop.domain.cart.dto.response.UpdateCartItemResponse;
import com.sparta.one_stop.domain.cart.support.GuestCartCookieProvider;
import com.sparta.one_stop.domain.cart.support.GuestCartRedisKeyProvider;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductItemRepository;
import com.sparta.one_stop.global.exception.CustomException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestCartServiceTest {

    private static final String GUEST_CART_ID = "guest-id";
    private static final String REDIS_KEY = "guest:cart:guest-id";
    private static final String ORDER_KEY = "guest:cart-order:guest-id";
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private ZSetOperations<String, String> zSetOperations;
    @Mock
    private ProductItemRepository productItemRepository;
    @Mock
    private GuestCartCookieProvider guestCartCookieProvider;
    @Mock
    private GuestCartRedisKeyProvider guestCartRedisKeyProvider;
    @Mock
    private HttpServletResponse response;
    @InjectMocks
    private GuestCartService guestCartService;

    @BeforeEach
    void setUp() {
        lenient().when(guestCartCookieProvider.resolveOrCreate(any(), eq(response)))
            .thenReturn(GUEST_CART_ID);
        lenient().when(guestCartRedisKeyProvider.buildGuestCartKey(GUEST_CART_ID))
            .thenReturn(REDIS_KEY);
        lenient().when(guestCartRedisKeyProvider.buildGuestCartOrderKey(GUEST_CART_ID))
            .thenReturn(ORDER_KEY);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    @DisplayName("addCartItem 성공 - 신규 상품이면 Hash에 quantity 저장 + ZSet에 순서 저장")
    void addCartItem_success_saveNewItem() {
        // given
        AddCartItemRequest request = new AddCartItemRequest(101L, 2);
        ProductItem productItem = productItem(101L, true, 10L);

        when(productItemRepository.findById(101L))
            .thenReturn(Optional.of(productItem));
        when(hashOperations.get(REDIS_KEY, "101"))
            .thenReturn(null);  // 신규 상품
        when(hashOperations.size(REDIS_KEY))
            .thenReturn(0L);    // 50종 미만
        when(redisTemplate.hasKey(anyString()))
            .thenReturn(true);

        // when
        CartItemResponse result = guestCartService.addCartItem(
            GUEST_CART_ID, response, request
        );

        // then
        verify(hashOperations).put(REDIS_KEY, "101", "2");
        verify(zSetOperations).add(eq(ORDER_KEY), eq("101"), anyDouble());
        verify(redisTemplate).expire(eq(REDIS_KEY), any());
        verify(redisTemplate).expire(eq(ORDER_KEY), any());
        verify(guestCartCookieProvider).refreshCookie(GUEST_CART_ID, response);

        assertThat(result.itemId()).isEqualTo(101L);
        assertThat(result.quantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("addCartItem 성공 - guestCartId가 없으면 쿠키를 새로 발급하고 장바구니에 담는다")
    void addCartItem_success_createGuestCartId_whenGuestCartIdIsNull() {
        // given
        AddCartItemRequest request = new AddCartItemRequest(101L, 2);
        ProductItem productItem = productItem(101L, true, 10L);

        when(productItemRepository.findById(101L))
            .thenReturn(Optional.of(productItem));
        when(hashOperations.get(REDIS_KEY, "101"))
            .thenReturn(null);
        when(hashOperations.size(REDIS_KEY))
            .thenReturn(0L);
        when(redisTemplate.hasKey(anyString()))
            .thenReturn(true);

        // when
        CartItemResponse result = guestCartService.addCartItem(
            null,
            response,
            request
        );

        // then
        verify(guestCartCookieProvider).resolveOrCreate(
            null,
            response
        );
        verify(guestCartRedisKeyProvider).buildGuestCartKey(GUEST_CART_ID);
        verify(guestCartRedisKeyProvider).buildGuestCartOrderKey(GUEST_CART_ID);

        verify(hashOperations).put(REDIS_KEY, "101", "2");
        verify(zSetOperations).add(eq(ORDER_KEY), eq("101"), anyDouble());

        verify(redisTemplate).expire(eq(REDIS_KEY), any());
        verify(redisTemplate).expire(eq(ORDER_KEY), any());
        verify(guestCartCookieProvider).refreshCookie(GUEST_CART_ID, response);

        assertThat(result.itemId()).isEqualTo(101L);
        assertThat(result.quantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("addCartItem 성공 - 기존 상품이면 quantity 증가 + ZSet score 유지")
    void addCartItem_success_increaseQuantity_whenItemAlreadyExists() {
        // given
        AddCartItemRequest request = new AddCartItemRequest(101L, 3);
        ProductItem productItem = productItem(101L, true, 10L);

        when(productItemRepository.findById(101L))
            .thenReturn(Optional.of(productItem));
        when(hashOperations.get(REDIS_KEY, "101"))
            .thenReturn("2");
        when(zSetOperations.score(ORDER_KEY, "101"))
            .thenReturn(12345.0);
        when(redisTemplate.hasKey(anyString()))
            .thenReturn(true);

        // when
        CartItemResponse result = guestCartService.addCartItem(
            GUEST_CART_ID,
            response,
            request
        );

        // then
        verify(hashOperations).put(REDIS_KEY, "101", "5");

        // 기존 상품이고 ZSet score가 이미 있으므로 최초 담기 순서는 변경하지 않는다
        verify(zSetOperations, never()).add(any(), any(), anyDouble());

        // 기존 상품 재담기에서는 50종 제한 검증이 필요 없으므로 size 조회를 하지 않는다
        verify(hashOperations, never()).size(REDIS_KEY);

        verify(redisTemplate).expire(eq(REDIS_KEY), any());
        verify(redisTemplate).expire(eq(ORDER_KEY), any());
        verify(guestCartCookieProvider).refreshCookie(GUEST_CART_ID, response);

        assertThat(result.itemId()).isEqualTo(101L);
        assertThat(result.quantity()).isEqualTo(5);
    }


    @Test
    @DisplayName("addCartItem 실패 - STOP 상품은 담을 수 없다")
    void addCartItem_fail_whenProductItemIsStop() {
        // given
        AddCartItemRequest request = new AddCartItemRequest(101L, 1);
        ProductItem productItem = stopProductItem();

        when(productItemRepository.findById(101L))
            .thenReturn(Optional.of(productItem));

        // when & then
        assertThatThrownBy(() -> guestCartService.addCartItem(
            GUEST_CART_ID, response, request
        )).isInstanceOf(CustomException.class);

        verify(hashOperations, never()).put(any(), any(), any());
        verify(zSetOperations, never()).add(any(), any(), anyDouble());
    }

    @Test
    @DisplayName("addCartItem 실패 - stock이 0인 상품은 담을 수 없다")
    void addCartItem_fail_whenStockIsZero() {
        // given
        AddCartItemRequest request = new AddCartItemRequest(101L, 1);
        ProductItem productItem = productItemForStockValidation(
            true,
            0L
        );

        when(productItemRepository.findById(101L))
            .thenReturn(Optional.of(productItem));

        // when & then
        assertThatThrownBy(() -> guestCartService.addCartItem(
            GUEST_CART_ID,
            response,
            request
        )).isInstanceOf(CustomException.class);

        verify(hashOperations, never()).get(any(), any());
        verify(hashOperations, never()).put(any(), any(), any());
        verify(zSetOperations, never()).add(any(), any(), anyDouble());
    }

    @Test
    @DisplayName("addCartItem 실패 - 요청 수량이 재고를 초과하면 예외 발생")
    void addCartItem_fail_whenQuantityExceedsStock() {
        // given
        AddCartItemRequest request = new AddCartItemRequest(101L, 5);
        ProductItem productItem = productItemForStockValidation(
            true,
            3L
        );

        when(productItemRepository.findById(101L))
            .thenReturn(Optional.of(productItem));

        // when & then
        assertThatThrownBy(() -> guestCartService.addCartItem(
            GUEST_CART_ID,
            response,
            request
        )).isInstanceOf(CustomException.class);

        verify(hashOperations, never()).get(any(), any());
        verify(hashOperations, never()).put(any(), any(), any());
        verify(zSetOperations, never()).add(any(), any(), anyDouble());
    }

    @Test
    @DisplayName("addCartItem 실패 - 비로그인 장바구니 상품 종류가 50종이면 신규 상품을 담을 수 없다")
    void addCartItem_fail_whenGuestCartItemCountIsAlready50() {
        // given
        AddCartItemRequest request = new AddCartItemRequest(101L, 1);

        ProductItem productItem = org.mockito.Mockito.mock(ProductItem.class);

        when(productItem.isOnSale()).thenReturn(true);
        when(productItem.getStock()).thenReturn(10L);

        when(productItemRepository.findById(101L))
            .thenReturn(Optional.of(productItem));
        when(hashOperations.get(REDIS_KEY, "101"))
            .thenReturn(null);
        when(hashOperations.size(REDIS_KEY))
            .thenReturn(50L);

        // when & then
        assertThatThrownBy(() -> guestCartService.addCartItem(
            GUEST_CART_ID,
            response,
            request
        ))
            .isInstanceOf(CustomException.class)
            .hasMessage("장바구니에 담을 수 있는 상품은 최대 50개입니다");

        verify(hashOperations, never()).put(any(), any(), any());
        verify(zSetOperations, never()).add(any(), any(), anyDouble());
        verify(guestCartCookieProvider, never()).refreshCookie(anyString(), any());
    }

    @Test
    @DisplayName("addCartItem 실패 - 요청 수량이 100개 이상이면 예외 발생")
    void addCartItem_fail_whenRequestQuantityExceedsMaxLimit() {
        // given
        AddCartItemRequest request = new AddCartItemRequest(101L, 100);

        ProductItem productItem = org.mockito.Mockito.mock(ProductItem.class);

        when(productItem.isOnSale()).thenReturn(true);

        when(productItemRepository.findById(101L))
            .thenReturn(Optional.of(productItem));

        // when & then
        assertThatThrownBy(() -> guestCartService.addCartItem(
            GUEST_CART_ID,
            response,
            request
        ))
            .isInstanceOf(CustomException.class)
            .hasMessage("수량은 1개 이상 99개 이하여야 합니다");

        verify(hashOperations, never()).get(any(), any());
        verify(hashOperations, never()).put(any(), any(), any());
        verify(zSetOperations, never()).add(any(), any(), anyDouble());
        verify(guestCartCookieProvider, never()).refreshCookie(anyString(), any());
    }

    @Test
    @DisplayName("updateCartItemQuantity 성공 - Hash quantity를 변경하고 TTL과 쿠키를 갱신한다")
    void updateCartItemQuantity_success() {
        // given
        Long itemId = 101L;
        UpdateCartItemRequest request = new UpdateCartItemRequest(4);
        ProductItem productItem = productItemForQuantityUpdate(10L);

        when(hashOperations.hasKey(REDIS_KEY, "101"))
            .thenReturn(true);
        when(productItemRepository.findById(itemId))
            .thenReturn(Optional.of(productItem));
        when(redisTemplate.hasKey(anyString()))
            .thenReturn(true);

        // when
        UpdateCartItemResponse result = guestCartService.updateCartItemQuantity(
            GUEST_CART_ID,
            response,
            itemId,
            request
        );

        // then
        verify(hashOperations).hasKey(REDIS_KEY, "101");
        verify(hashOperations).put(REDIS_KEY, "101", "4");

        verify(redisTemplate).expire(eq(REDIS_KEY), any());
        verify(redisTemplate).expire(eq(ORDER_KEY), any());
        verify(guestCartCookieProvider).refreshCookie(GUEST_CART_ID, response);

        assertThat(result.itemId()).isEqualTo(101L);
        assertThat(result.quantity()).isEqualTo(4);
    }

    @Test
    @DisplayName("updateCartItemQuantity 실패 - Redis Hash에 해당 itemId가 없으면 예외 발생")
    void updateCartItemQuantity_fail_whenItemIdDoesNotExistInGuestCart() {
        // given
        Long itemId = 101L;
        UpdateCartItemRequest request = new UpdateCartItemRequest(2);

        when(hashOperations.hasKey(REDIS_KEY, "101"))
            .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> guestCartService.updateCartItemQuantity(
            GUEST_CART_ID,
            response,
            itemId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .hasMessage("장바구니에 해당 상품이 없습니다");

        verify(productItemRepository, never()).findById(any());
        verify(hashOperations, never()).put(any(), any(), any());
        verify(guestCartCookieProvider, never()).refreshCookie(anyString(), any());
    }

    @Test
    @DisplayName("updateCartItemQuantity 실패 - 요청 수량이 100개 이상이면 예외 발생")
    void updateCartItemQuantity_fail_whenRequestQuantityExceedsMaxLimit() {
        // given
        Long itemId = 101L;
        UpdateCartItemRequest request = new UpdateCartItemRequest(100);

        ProductItem productItem = org.mockito.Mockito.mock(ProductItem.class);

        when(productItem.isOnSale()).thenReturn(true);

        when(hashOperations.hasKey(REDIS_KEY, "101"))
            .thenReturn(true);
        when(productItemRepository.findById(itemId))
            .thenReturn(Optional.of(productItem));

        // when & then
        assertThatThrownBy(() -> guestCartService.updateCartItemQuantity(
            GUEST_CART_ID,
            response,
            itemId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .hasMessage("수량은 1개 이상 99개 이하여야 합니다");

        verify(hashOperations, never()).put(any(), any(), any());
        verify(redisTemplate, never()).expire(anyString(), any());
        verify(guestCartCookieProvider, never()).refreshCookie(anyString(), any());
    }

    @Test
    @DisplayName("updateCartItemQuantity 실패 - 요청 수량이 재고를 초과하면 예외 발생")
    void updateCartItemQuantity_fail_whenRequestQuantityExceedsStock() {
        // given
        Long itemId = 101L;
        UpdateCartItemRequest request = new UpdateCartItemRequest(5);
        ProductItem productItem = productItemForQuantityUpdate(3L);

        when(hashOperations.hasKey(REDIS_KEY, "101"))
            .thenReturn(true);
        when(productItemRepository.findById(itemId))
            .thenReturn(Optional.of(productItem));

        // when & then
        assertThatThrownBy(() -> guestCartService.updateCartItemQuantity(
            GUEST_CART_ID,
            response,
            itemId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .hasMessage("재고가 부족합니다");

        verify(hashOperations, never()).put(any(), any(), any());
        verify(redisTemplate, never()).expire(anyString(), any());
        verify(guestCartCookieProvider, never()).refreshCookie(anyString(), any());
    }

    @Test
    @DisplayName("updateCartItemQuantity 실패 - STOP 상품은 수량을 변경할 수 없다")
    void updateCartItemQuantity_fail_whenProductItemIsStop() {
        // given
        Long itemId = 101L;
        UpdateCartItemRequest request = new UpdateCartItemRequest(2);
        ProductItem productItem = stopProductItem();

        when(hashOperations.hasKey(REDIS_KEY, "101"))
            .thenReturn(true);

        when(productItemRepository.findById(itemId))
            .thenReturn(Optional.of(productItem));

        // when & then
        assertThatThrownBy(() -> guestCartService.updateCartItemQuantity(
            GUEST_CART_ID,
            response,
            itemId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .hasMessage("장바구니에 담을 수 없는 상품입니다");

        verify(hashOperations, never()).put(any(), any(), any());
        verify(redisTemplate, never()).expire(anyString(), any());
        verify(guestCartCookieProvider, never()).refreshCookie(anyString(), any());
    }

    @Test
    @DisplayName("deleteCartItem 성공 - Hash/ZSet에서 itemId를 제거하고 남은 상품이 있으면 TTL과 쿠키를 갱신한다")
    void deleteCartItem_success_whenRemainingItemsExist() {
        // given
        Long itemId = 101L;

        when(hashOperations.hasKey(REDIS_KEY, "101"))
            .thenReturn(true);
        when(hashOperations.size(REDIS_KEY))
            .thenReturn(1L);
        when(redisTemplate.hasKey(anyString()))
            .thenReturn(true);

        // when
        guestCartService.deleteCartItem(
            GUEST_CART_ID,
            response,
            itemId
        );

        // then
        verify(hashOperations).delete(REDIS_KEY, "101");
        verify(zSetOperations).remove(ORDER_KEY, "101");

        verify(redisTemplate, never()).delete(REDIS_KEY);
        verify(redisTemplate, never()).delete(ORDER_KEY);

        verify(redisTemplate).expire(eq(REDIS_KEY), any());
        verify(redisTemplate).expire(eq(ORDER_KEY), any());
        verify(guestCartCookieProvider).refreshCookie(GUEST_CART_ID, response);
    }

    @Test
    @DisplayName("deleteCartItem 성공 - 마지막 상품 삭제 시 Hash/ZSet key를 삭제하고 쿠키는 갱신한다")
    void deleteCartItem_success_deleteRedisKeys_whenLastItemDeleted() {
        // given
        Long itemId = 101L;

        when(hashOperations.hasKey(REDIS_KEY, "101"))
            .thenReturn(true);
        when(hashOperations.size(REDIS_KEY))
            .thenReturn(0L);

        // when
        guestCartService.deleteCartItem(
            GUEST_CART_ID,
            response,
            itemId
        );

        // then
        verify(hashOperations).delete(REDIS_KEY, "101");
        verify(zSetOperations).remove(ORDER_KEY, "101");

        verify(redisTemplate).delete(REDIS_KEY);
        verify(redisTemplate).delete(ORDER_KEY);

        verify(guestCartCookieProvider).refreshCookie(GUEST_CART_ID, response);
    }

    @Test
    @DisplayName("deleteCartItem 실패 - Redis Hash에 해당 itemId가 없으면 예외 발생")
    void deleteCartItem_fail_whenItemIdDoesNotExistInRedisHash() {
        // given
        Long itemId = 101L;

        when(hashOperations.hasKey(
            REDIS_KEY,
            "101"
        )).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> guestCartService.deleteCartItem(
            GUEST_CART_ID,
            response,
            itemId
        ))
            .isInstanceOf(CustomException.class)
            .hasMessage("장바구니에 해당 상품이 없습니다");

        verify(hashOperations, never()).delete(any(), any());
        verify(zSetOperations, never()).remove(any(), any());
        verify(hashOperations, never()).size(any());
        verify(redisTemplate, never()).delete(anyString());
        verify(redisTemplate, never()).expire(anyString(), any());
        verify(guestCartCookieProvider, never()).refreshCookie(anyString(), any());
    }

    @Test
    @DisplayName("getCart 성공 - ZSet 담기 순서 기준으로 장바구니를 조회한다")
    void getCart_success_returnItemsByZSetOrder() {
        // given
        Pageable pageable = PageRequest.of(
            0,
            20
        );

        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("101", "1");
        entries.put("104", "2");

        Set<String> orderedItemFields = new LinkedHashSet<>();
        orderedItemFields.add("104");
        orderedItemFields.add("101");

        ProductItem productItem101 = productItemForCartDetail(
            101L,
            1L,
            "상품 101",
            "옵션 101",
            1000L,
            10L,
            true,
            "thumbnail-101.jpg"
        );

        ProductItem productItem104 = productItemForCartDetail(
            104L,
            1L,
            "상품 104",
            "옵션 104",
            2000L,
            10L,
            true,
            "thumbnail-104.jpg"
        );

        when(hashOperations.entries(REDIS_KEY))
            .thenReturn(entries);
        when(redisTemplate.hasKey(anyString()))
            .thenReturn(true);
        when(zSetOperations.range(ORDER_KEY, 0, -1))
            .thenReturn(orderedItemFields);
        when(productItemRepository.findAllByIdInWithProduct(
            List.of(104L, 101L)
        )).thenReturn(List.of(
            productItem101,
            productItem104
        ));

        // when
        CartPageResponse result = guestCartService.getCart(
            GUEST_CART_ID,
            response,
            pageable
        );

        // then
        verify(redisTemplate).expire(eq(REDIS_KEY), any());
        verify(redisTemplate).expire(eq(ORDER_KEY), any());
        verify(guestCartCookieProvider).refreshCookie(GUEST_CART_ID, response);

        assertThat(result.cartId()).isNull();
        assertThat(result.content()).hasSize(2);

        CartItemDetailResponse firstItem = result.content().get(0);
        CartItemDetailResponse secondItem = result.content().get(1);

        assertThat(firstItem.itemId()).isEqualTo(104L);
        assertThat(firstItem.quantity()).isEqualTo(2);

        assertThat(secondItem.itemId()).isEqualTo(101L);
        assertThat(secondItem.quantity()).isEqualTo(1);

        assertThat(result.totalPrice()).isEqualTo(5000L);
        assertThat(result.itemCount()).isEqualTo(3);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("getCart 성공 - Redis 수량 파싱 실패 항목은 제외하고 조회한다")
    void getCart_success_skipInvalidQuantityValues() {
        // given
        Pageable pageable = PageRequest.of(
            0,
            20
        );

        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("101", "abc"); // 파싱 실패
        entries.put("102", "0");   // 범위 오류
        entries.put("103", "-1");  // 범위 오류
        entries.put("104", "2");   // 정상

        Set<String> orderedItemFields = new LinkedHashSet<>();
        orderedItemFields.add("101");
        orderedItemFields.add("102");
        orderedItemFields.add("103");
        orderedItemFields.add("104");

        ProductItem productItem104 = productItemForCartDetail(
            104L,
            1L,
            "상품 104",
            "옵션 104",
            2000L,
            10L,
            true,
            "thumbnail-104.jpg"
        );

        when(hashOperations.entries(REDIS_KEY))
            .thenReturn(entries);
        when(redisTemplate.hasKey(anyString()))
            .thenReturn(true);
        when(zSetOperations.range(ORDER_KEY, 0, -1))
            .thenReturn(orderedItemFields);
        when(productItemRepository.findAllByIdInWithProduct(
            List.of(104L)
        )).thenReturn(List.of(productItem104));

        // when
        CartPageResponse result = guestCartService.getCart(
            GUEST_CART_ID,
            response,
            pageable
        );

        // then
        assertThat(result.content()).hasSize(1);

        CartItemDetailResponse item = result.content().get(0);

        assertThat(item.itemId()).isEqualTo(104L);
        assertThat(item.quantity()).isEqualTo(2);
        assertThat(result.totalPrice()).isEqualTo(4000L);
        assertThat(result.itemCount()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);

        verify(productItemRepository).findAllByIdInWithProduct(
            List.of(104L)
        );
    }

    @Test
    @DisplayName("getCart 성공 - Redis Hash가 비어 있으면 빈 장바구니를 반환한다")
    void getCart_success_returnEmptyResult_whenRedisHashIsEmpty() {
        // given
        Pageable pageable = PageRequest.of(
            0,
            20
        );

        when(hashOperations.entries(REDIS_KEY))
            .thenReturn(Map.of());

        when(redisTemplate.hasKey(anyString()))
            .thenReturn(false);

        // when
        CartPageResponse result = guestCartService.getCart(
            GUEST_CART_ID,
            response,
            pageable
        );

        // then
        assertThat(result.cartId()).isNull();
        assertThat(result.content()).isEmpty();
        assertThat(result.totalPrice()).isZero();
        assertThat(result.itemCount()).isZero();
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();

        verify(productItemRepository, never()).findAllByIdInWithProduct(any());
        verify(redisTemplate, never()).expire(anyString(), any());
        verify(guestCartCookieProvider).refreshCookie(
            GUEST_CART_ID,
            response
        );
    }

    @Test
    @DisplayName("getCart 성공 - Redis ZSet이 null이면 itemId DESC 기준으로 폴백 정렬한다")
    void getCart_success_sortByItemIdDesc_whenRedisZSetIsNull() {
        // given
        Pageable pageable = PageRequest.of(
            0,
            20
        );

        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("101", "1");
        entries.put("103", "2");
        entries.put("102", "3");

        ProductItem productItem101 = productItemForCartDetail(
            101L,
            1L,
            "상품 101",
            "옵션 101",
            1000L,
            10L,
            true,
            "thumbnail-101.jpg"
        );

        ProductItem productItem102 = productItemForCartDetail(
            102L,
            1L,
            "상품 102",
            "옵션 102",
            2000L,
            10L,
            true,
            "thumbnail-102.jpg"
        );

        ProductItem productItem103 = productItemForCartDetail(
            103L,
            1L,
            "상품 103",
            "옵션 103",
            3000L,
            10L,
            true,
            "thumbnail-103.jpg"
        );

        when(hashOperations.entries(REDIS_KEY))
            .thenReturn(entries);

        when(redisTemplate.hasKey(anyString()))
            .thenReturn(true);

        when(zSetOperations.range(
            ORDER_KEY,
            0,
            -1
        )).thenReturn(null);

        when(productItemRepository.findAllByIdInWithProduct(
            List.of(
                103L,
                102L,
                101L
            )
        )).thenReturn(List.of(
            productItem101,
            productItem102,
            productItem103
        ));

        // when
        CartPageResponse result = guestCartService.getCart(
            GUEST_CART_ID,
            response,
            pageable
        );

        // then
        verify(zSetOperations).range(
            ORDER_KEY,
            0,
            -1
        );

        verify(productItemRepository).findAllByIdInWithProduct(
            List.of(
                103L,
                102L,
                101L
            )
        );

        assertThat(result.content()).hasSize(3);

        assertThat(result.content().get(0).itemId()).isEqualTo(103L);
        assertThat(result.content().get(0).quantity()).isEqualTo(2);

        assertThat(result.content().get(1).itemId()).isEqualTo(102L);
        assertThat(result.content().get(1).quantity()).isEqualTo(3);

        assertThat(result.content().get(2).itemId()).isEqualTo(101L);
        assertThat(result.content().get(2).quantity()).isEqualTo(1);

        assertThat(result.totalPrice()).isEqualTo(13000L);
        assertThat(result.itemCount()).isEqualTo(6);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(1);

        verify(redisTemplate).expire(eq(REDIS_KEY), any());
        verify(redisTemplate).expire(eq(ORDER_KEY), any());
        verify(guestCartCookieProvider).refreshCookie(
            GUEST_CART_ID,
            response
        );
    }

    @Test
    @DisplayName("getCart 성공 - Redis ZSet이 비어 있으면 itemId DESC 기준으로 폴백 정렬한다")
    void getCart_success_sortByItemIdDesc_whenRedisZSetIsEmpty() {
        // given
        Pageable pageable = PageRequest.of(
            0,
            20
        );

        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("101", "1");
        entries.put("103", "2");
        entries.put("102", "3");

        ProductItem productItem101 = productItemForCartDetail(
            101L,
            1L,
            "상품 101",
            "옵션 101",
            1000L,
            10L,
            true,
            "thumbnail-101.jpg"
        );

        ProductItem productItem102 = productItemForCartDetail(
            102L,
            1L,
            "상품 102",
            "옵션 102",
            2000L,
            10L,
            true,
            "thumbnail-102.jpg"
        );

        ProductItem productItem103 = productItemForCartDetail(
            103L,
            1L,
            "상품 103",
            "옵션 103",
            3000L,
            10L,
            true,
            "thumbnail-103.jpg"
        );

        when(hashOperations.entries(REDIS_KEY))
            .thenReturn(entries);

        when(redisTemplate.hasKey(anyString()))
            .thenReturn(true);

        when(zSetOperations.range(
            ORDER_KEY,
            0,
            -1
        )).thenReturn(Set.of());

        when(productItemRepository.findAllByIdInWithProduct(
            List.of(
                103L,
                102L,
                101L
            )
        )).thenReturn(List.of(
            productItem101,
            productItem102,
            productItem103
        ));

        // when
        CartPageResponse result = guestCartService.getCart(
            GUEST_CART_ID,
            response,
            pageable
        );

        // then
        verify(productItemRepository).findAllByIdInWithProduct(
            List.of(
                103L,
                102L,
                101L
            )
        );

        assertThat(result.content()).hasSize(3);

        assertThat(result.content().get(0).itemId()).isEqualTo(103L);
        assertThat(result.content().get(1).itemId()).isEqualTo(102L);
        assertThat(result.content().get(2).itemId()).isEqualTo(101L);

        assertThat(result.totalPrice()).isEqualTo(13000L);
        assertThat(result.itemCount()).isEqualTo(6);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("addCartItem 성공 - 기존 Redis 수량이 파싱 불가능하면 0으로 간주하고 요청 수량으로 저장한다")
    void addCartItem_success_recoverQuantity_whenStoredQuantityIsInvalid() {
        // given
        AddCartItemRequest request = new AddCartItemRequest(101L, 3);
        ProductItem productItem = productItem(101L, true, 10L);

        when(productItemRepository.findById(101L))
            .thenReturn(Optional.of(productItem));
        when(hashOperations.get(REDIS_KEY, "101"))
            .thenReturn("abc");
        when(zSetOperations.score(ORDER_KEY, "101"))
            .thenReturn(12345.0);
        when(redisTemplate.hasKey(anyString()))
            .thenReturn(true);

        // when
        CartItemResponse result = guestCartService.addCartItem(
            GUEST_CART_ID,
            response,
            request
        );

        // then
        verify(hashOperations).put(REDIS_KEY, "101", "3");

        // 기존 상품이고 ZSet score가 이미 있으므로 최초 담기 순서는 변경하지 않는다
        verify(zSetOperations, never()).add(any(), any(), anyDouble());

        // 기존 상품 재담기에서는 50종 제한 검증이 필요 없으므로 size 조회를 하지 않는다
        verify(hashOperations, never()).size(REDIS_KEY);

        verify(redisTemplate).expire(eq(REDIS_KEY), any());
        verify(redisTemplate).expire(eq(ORDER_KEY), any());
        verify(guestCartCookieProvider).refreshCookie(GUEST_CART_ID, response);

        assertThat(result.itemId()).isEqualTo(101L);
        assertThat(result.quantity()).isEqualTo(3);
    }

    private ProductItem productItem(Long itemId, boolean onSale, Long stock) {
        ProductItem productItem = org.mockito.Mockito.mock(ProductItem.class);

        when(productItem.getId()).thenReturn(itemId);
        when(productItem.isOnSale()).thenReturn(onSale);

        if (stock != null) {
            when(productItem.getStock()).thenReturn(stock);
        }

        return productItem;
    }

    private ProductItem stopProductItem() {
        ProductItem productItem = org.mockito.Mockito.mock(ProductItem.class);

        when(productItem.isOnSale()).thenReturn(false);

        return productItem;
    }

    private ProductItem productItemForStockValidation(
        boolean onSale,
        Long stock
    ) {
        ProductItem productItem = org.mockito.Mockito.mock(ProductItem.class);

        when(productItem.isOnSale()).thenReturn(onSale);
        when(productItem.getStock()).thenReturn(stock);

        return productItem;
    }

    private ProductItem productItemForQuantityUpdate(Long stock) {
        ProductItem productItem = org.mockito.Mockito.mock(ProductItem.class);

        when(productItem.isOnSale()).thenReturn(true);
        when(productItem.getStock()).thenReturn(stock);

        return productItem;
    }

    private ProductItem productItemForCartDetail(
        Long itemId,
        Long productId,
        String productName,
        String optionName,
        Long price,
        Long stock,
        boolean onSale,
        String thumbnailUrl
    ) {
        ProductItem productItem = org.mockito.Mockito.mock(ProductItem.class);
        Product product = org.mockito.Mockito.mock(Product.class);

        when(productItem.getId()).thenReturn(itemId);
        when(productItem.getProduct()).thenReturn(product);
        when(productItem.getOptionSummary()).thenReturn(optionName);
        when(productItem.getPrice()).thenReturn(price);
        when(productItem.getStock()).thenReturn(stock);
        when(productItem.isOnSale()).thenReturn(onSale);

        when(product.getId()).thenReturn(productId);
        when(product.getName()).thenReturn(productName);
        when(product.getThumbnailUrl()).thenReturn(thumbnailUrl);

        return productItem;
    }

}
