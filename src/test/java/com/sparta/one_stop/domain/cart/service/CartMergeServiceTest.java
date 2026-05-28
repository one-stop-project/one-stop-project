package com.sparta.one_stop.domain.cart.service;

import com.sparta.one_stop.domain.cart.support.GuestCartRedisKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartMergeServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private CartMergeExecutor cartMergeExecutor;

    @Mock
    private GuestCartRedisKeyProvider guestCartRedisKeyProvider;

    @InjectMocks
    private CartMergeService cartMergeService;

    @Test
    @DisplayName("merge 실패 - guestCartId가 null이면 false 반환")
    void mergeGuestCartToUserCart_returnFalse_whenGuestCartIdIsNull() {
        // given
        Long userId = 1L;
        String guestCartId = null;

        // when
        boolean result = cartMergeService.mergeGuestCartToUserCart(
            userId,
            guestCartId
        );

        // then
        assertThat(result).isFalse();

        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(cartMergeExecutor);
        verifyNoInteractions(guestCartRedisKeyProvider);
    }

    @Test
    @DisplayName("merge 실패 - guestCartId가 blank이면 false 반환")
    void mergeGuestCartToUserCart_returnFalse_whenGuestCartIdIsBlank() {
        // given
        Long userId = 1L;
        String guestCartId = " ";

        // when
        boolean result = cartMergeService.mergeGuestCartToUserCart(
            userId,
            guestCartId
        );

        // then
        assertThat(result).isFalse();

        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(cartMergeExecutor);
        verifyNoInteractions(guestCartRedisKeyProvider);
    }

    @Test
    @DisplayName("merge 성공 - Redis Hash가 비어 있으면 Hash/ZSet key 삭제 후 true 반환")
    void mergeGuestCartToUserCart_success_whenRedisHashIsEmpty() {
        // given
        Long userId = 1L;
        String guestCartId = "guest-id";
        String redisKey = "guest:cart:guest-id";
        String orderKey = "guest:cart-order:guest-id";

        when(guestCartRedisKeyProvider.buildGuestCartKey(guestCartId))
            .thenReturn(redisKey);
        when(guestCartRedisKeyProvider.buildGuestCartOrderKey(guestCartId))
            .thenReturn(orderKey);

        when(redisTemplate.opsForHash())
            .thenReturn(hashOperations);
        when(hashOperations.entries(redisKey))
            .thenReturn(Map.of());

        // when
        boolean result = cartMergeService.mergeGuestCartToUserCart(
            userId,
            guestCartId
        );

        // then
        assertThat(result).isTrue();

        verify(redisTemplate).delete(redisKey);
        verify(redisTemplate).delete(orderKey);
        verify(cartMergeExecutor, never()).execute(
            eq(userId),
            anyMap()
        );
    }

    @Test
    @DisplayName("merge 성공 - ZSet 순서대로 Redis 장바구니를 Executor에 전달")
    void mergeGuestCartToUserCart_success_withZSetOrder() {
        // given
        Long userId = 1L;
        String guestCartId = "guest-id";
        String redisKey = "guest:cart:guest-id";
        String orderKey = "guest:cart-order:guest-id";

        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("101", "1");
        entries.put("104", "2");
        entries.put("105", "3");

        Set<String> orderedItemFields = new LinkedHashSet<>();
        orderedItemFields.add("104");
        orderedItemFields.add("101");
        orderedItemFields.add("105");

        when(guestCartRedisKeyProvider.buildGuestCartKey(guestCartId))
            .thenReturn(redisKey);
        when(guestCartRedisKeyProvider.buildGuestCartOrderKey(guestCartId))
            .thenReturn(orderKey);

        when(redisTemplate.opsForHash())
            .thenReturn(hashOperations);
        when(hashOperations.entries(redisKey))
            .thenReturn(entries);

        when(redisTemplate.opsForZSet())
            .thenReturn(zSetOperations);
        when(zSetOperations.range(orderKey, 0, -1))
            .thenReturn(orderedItemFields);

        ArgumentCaptor<Map<Long, Integer>> mapCaptor = mapCaptor();

        // when
        boolean result = cartMergeService.mergeGuestCartToUserCart(
            userId,
            guestCartId
        );

        // then
        assertThat(result).isTrue();

        verify(cartMergeExecutor).execute(
            eq(userId),
            mapCaptor.capture()
        );

        Map<Long, Integer> capturedMap = mapCaptor.getValue();

        assertThat(capturedMap.keySet())
            .containsExactly(104L, 101L, 105L);

        assertThat(capturedMap)
            .containsEntry(104L, 2)
            .containsEntry(101L, 1)
            .containsEntry(105L, 3);

        verify(redisTemplate).delete(redisKey);
        verify(redisTemplate).delete(orderKey);
    }

    @Test
    @DisplayName("merge 성공 - ZSet에 없는 Hash-only 데이터는 뒤에 병합")
    void mergeGuestCartToUserCart_success_appendHashOnlyDataAfterZSetOrder() {
        // given
        Long userId = 1L;
        String guestCartId = "guest-id";
        String redisKey = "guest:cart:guest-id";
        String orderKey = "guest:cart-order:guest-id";

        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("101", "1");
        entries.put("102", "2");
        entries.put("104", "4");
        entries.put("105", "5");

        Set<String> orderedItemFields = new LinkedHashSet<>();
        orderedItemFields.add("104");
        orderedItemFields.add("101");

        when(guestCartRedisKeyProvider.buildGuestCartKey(guestCartId))
            .thenReturn(redisKey);
        when(guestCartRedisKeyProvider.buildGuestCartOrderKey(guestCartId))
            .thenReturn(orderKey);

        when(redisTemplate.opsForHash())
            .thenReturn(hashOperations);
        when(hashOperations.entries(redisKey))
            .thenReturn(entries);

        when(redisTemplate.opsForZSet())
            .thenReturn(zSetOperations);
        when(zSetOperations.range(orderKey, 0, -1))
            .thenReturn(orderedItemFields);

        ArgumentCaptor<Map<Long, Integer>> mapCaptor = mapCaptor();

        // when
        boolean result = cartMergeService.mergeGuestCartToUserCart(
            userId,
            guestCartId
        );

        // then
        assertThat(result).isTrue();

        verify(cartMergeExecutor).execute(
            eq(userId),
            mapCaptor.capture()
        );

        Map<Long, Integer> capturedMap = mapCaptor.getValue();

        assertThat(capturedMap.keySet())
            .containsExactly(104L, 101L, 105L, 102L);

        assertThat(capturedMap)
            .containsEntry(104L, 4)
            .containsEntry(101L, 1)
            .containsEntry(105L, 5)
            .containsEntry(102L, 2);

        verify(redisTemplate).delete(redisKey);
        verify(redisTemplate).delete(orderKey);
    }

    @Test
    @DisplayName("merge 성공 - 파싱 가능한 Redis 데이터가 없으면 Hash/ZSet key 삭제 후 true 반환")
    void mergeGuestCartToUserCart_success_whenParsedMapIsEmpty() {
        // given
        Long userId = 1L;
        String guestCartId = "guest-id";
        String redisKey = "guest:cart:guest-id";
        String orderKey = "guest:cart-order:guest-id";

        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("invalid-item-id", "1");
        entries.put("101", "invalid-quantity");

        when(guestCartRedisKeyProvider.buildGuestCartKey(guestCartId))
            .thenReturn(redisKey);
        when(guestCartRedisKeyProvider.buildGuestCartOrderKey(guestCartId))
            .thenReturn(orderKey);

        when(redisTemplate.opsForHash())
            .thenReturn(hashOperations);
        when(hashOperations.entries(redisKey))
            .thenReturn(entries);

        when(redisTemplate.opsForZSet())
            .thenReturn(zSetOperations);
        when(zSetOperations.range(orderKey, 0, -1))
            .thenReturn(Set.of("101"));

        // when
        boolean result = cartMergeService.mergeGuestCartToUserCart(
            userId,
            guestCartId
        );

        // then
        assertThat(result).isTrue();

        verify(cartMergeExecutor, never()).execute(
            eq(userId),
            anyMap()
        );

        verify(redisTemplate).delete(redisKey);
        verify(redisTemplate).delete(orderKey);
    }

    @Test
    @DisplayName("merge 성공 - ZSet이 없으면 Hash itemId DESC 기준으로 merge")
    void mergeGuestCartToUserCart_success_whenZSetIsEmpty() {
        // given
        Long userId = 1L;
        String guestCartId = "guest-id";
        String redisKey = "guest:cart:guest-id";
        String orderKey = "guest:cart-order:guest-id";

        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("101", "1");
        entries.put("104", "4");
        entries.put("102", "2");

        when(guestCartRedisKeyProvider.buildGuestCartKey(guestCartId))
            .thenReturn(redisKey);
        when(guestCartRedisKeyProvider.buildGuestCartOrderKey(guestCartId))
            .thenReturn(orderKey);

        when(redisTemplate.opsForHash())
            .thenReturn(hashOperations);
        when(hashOperations.entries(redisKey))
            .thenReturn(entries);

        when(redisTemplate.opsForZSet())
            .thenReturn(zSetOperations);
        when(zSetOperations.range(orderKey, 0, -1))
            .thenReturn(Set.of());  // ZSet 비어있음

        ArgumentCaptor<Map<Long, Integer>> mapCaptor = mapCaptor();

        // when
        boolean result = cartMergeService.mergeGuestCartToUserCart(
            userId,
            guestCartId
        );

        // then
        assertThat(result).isTrue();

        verify(cartMergeExecutor).execute(
            eq(userId),
            mapCaptor.capture()
        );

        Map<Long, Integer> capturedMap = mapCaptor.getValue();

        // ZSet 없으면 itemId DESC 정렬 → 104, 102, 101
        assertThat(capturedMap.keySet())
            .containsExactly(104L, 102L, 101L);

        assertThat(capturedMap)
            .containsEntry(104L, 4)
            .containsEntry(102L, 2)
            .containsEntry(101L, 1);

        verify(redisTemplate).delete(redisKey);
        verify(redisTemplate).delete(orderKey);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<Long, Integer>> mapCaptor() {
        return ArgumentCaptor.forClass(Map.class);
    }

}
