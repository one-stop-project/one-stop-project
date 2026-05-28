package com.sparta.one_stop.domain.cart.service;

import com.sparta.one_stop.domain.cart.entity.Cart;
import com.sparta.one_stop.domain.cart.entity.CartItem;
import com.sparta.one_stop.domain.cart.repository.CartItemRepository;
import com.sparta.one_stop.domain.cart.repository.CartRepository;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductItemRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartMergeExecutorTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductItemRepository productItemRepository;

    @InjectMocks
    private CartMergeExecutor cartMergeExecutor;

    @Test
    @DisplayName("merge 중단 - guestQuantityMap이 null이면 아무 작업도 하지 않는다")
    void execute_doNothing_whenGuestQuantityMapIsNull() {
        // given
        Long userId = 1L;
        Map<Long, Integer> guestQuantityMap = null;

        // when
        cartMergeExecutor.execute(
            userId,
            guestQuantityMap
        );

        // then
        verifyNoInteractions(userRepository);
        verifyNoInteractions(cartRepository);
        verifyNoInteractions(cartItemRepository);
        verifyNoInteractions(productItemRepository);
    }

    @Test
    @DisplayName("merge 중단 - guestQuantityMap이 비어 있으면 아무 작업도 하지 않는다")
    void execute_doNothing_whenGuestQuantityMapIsEmpty() {
        // given
        Long userId = 1L;
        Map<Long, Integer> guestQuantityMap = Map.of();

        // when
        cartMergeExecutor.execute(
            userId,
            guestQuantityMap
        );

        // then
        verifyNoInteractions(userRepository);
        verifyNoInteractions(cartRepository);
        verifyNoInteractions(cartItemRepository);
        verifyNoInteractions(productItemRepository);
    }

    @Test
    @DisplayName("merge 성공 - DB 장바구니가 없으면 새 Cart를 생성한다")
    void execute_success_createCart_whenCartDoesNotExist() {
        // given
        Long userId = 1L;
        User user = user();
        Cart savedCart = cart(10L);

        ProductItem productItem = productItem(
            101L,
            true,
            10L
        );

        Map<Long, Integer> guestQuantityMap = new LinkedHashMap<>();
        guestQuantityMap.put(101L, 2);

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(userId))
            .thenReturn(user);
        when(cartRepository.save(any(Cart.class)))
            .thenReturn(savedCart);

        when(productItemRepository.findAllByIdInWithProduct(List.of(101L)))
            .thenReturn(List.of(productItem));

        when(cartItemRepository.findAllByCartIdWithProductItem(savedCart.getId()))
            .thenReturn(List.of());

        // when
        cartMergeExecutor.execute(
            userId,
            guestQuantityMap
        );

        // then
        verify(userRepository).getReferenceById(userId);
        verify(cartRepository).save(any(Cart.class));

        ArgumentCaptor<CartItem> cartItemCaptor =
            ArgumentCaptor.forClass(CartItem.class);

        verify(cartItemRepository).save(cartItemCaptor.capture());

        CartItem savedCartItem = cartItemCaptor.getValue();

        assertThat(savedCartItem.getCart()).isSameAs(savedCart);
        assertThat(savedCartItem.getProductItem()).isSameAs(productItem);
        assertThat(savedCartItem.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("merge 성공 - 기존 CartItem이 있으면 수량을 합산한다")
    void execute_success_mergeExistingCartItem() {
        // given
        Long userId = 1L;
        Cart cart = cart(10L);

        ProductItem productItem = productItem(
            101L,
            true,
            20L
        );

        CartItem existingCartItem = existingCartItem(
            productItem,
            3
        );

        Map<Long, Integer> guestQuantityMap = new LinkedHashMap<>();
        guestQuantityMap.put(101L, 2);

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));

        when(productItemRepository.findAllByIdInWithProduct(List.of(101L)))
            .thenReturn(List.of(productItem));

        when(cartItemRepository.findAllByCartIdWithProductItem(cart.getId()))
            .thenReturn(List.of(existingCartItem));

        // when
        cartMergeExecutor.execute(
            userId,
            guestQuantityMap
        );

        // then
        verify(existingCartItem).changeQuantity(5);
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("merge 성공 - 기존 CartItem 수량 합산 결과가 99개를 초과하면 99개로 보정한다")
    void execute_success_capQuantityAtMax99() {
        // given
        Long userId = 1L;
        Cart cart = cart(10L);

        ProductItem productItem = productItem(
            101L,
            true,
            200L
        );

        CartItem existingCartItem = existingCartItem(
            productItem,
            90
        );

        Map<Long, Integer> guestQuantityMap = new LinkedHashMap<>();
        guestQuantityMap.put(101L, 20);

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));

        when(productItemRepository.findAllByIdInWithProduct(List.of(101L)))
            .thenReturn(List.of(productItem));

        when(cartItemRepository.findAllByCartIdWithProductItem(cart.getId()))
            .thenReturn(List.of(existingCartItem));

        // when
        cartMergeExecutor.execute(
            userId,
            guestQuantityMap
        );

        // then
        verify(existingCartItem).changeQuantity(99);
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("merge 성공 - 기존 CartItem 수량 합산 결과가 재고를 초과하면 재고 수량으로 보정한다")
    void execute_success_capQuantityAtStock() {
        // given
        Long userId = 1L;
        Cart cart = cart(10L);

        ProductItem productItem = productItem(
            101L,
            true,
            5L
        );

        CartItem existingCartItem = existingCartItem(
            productItem,
            3
        );

        Map<Long, Integer> guestQuantityMap = new LinkedHashMap<>();
        guestQuantityMap.put(101L, 10);

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));

        when(productItemRepository.findAllByIdInWithProduct(List.of(101L)))
            .thenReturn(List.of(productItem));

        when(cartItemRepository.findAllByCartIdWithProductItem(cart.getId()))
            .thenReturn(List.of(existingCartItem));

        // when
        cartMergeExecutor.execute(
            userId,
            guestQuantityMap
        );

        // then
        verify(existingCartItem).changeQuantity(5);
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("merge 성공 - 신규 itemId는 CartItem으로 저장한다")
    void execute_success_saveNewCartItem() {
        // given
        Long userId = 1L;
        Cart cart = cart(10L);

        ProductItem productItem = productItem(
            104L,
            true,
            10L
        );

        Map<Long, Integer> guestQuantityMap = new LinkedHashMap<>();
        guestQuantityMap.put(104L, 4);

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));

        when(productItemRepository.findAllByIdInWithProduct(List.of(104L)))
            .thenReturn(List.of(productItem));

        when(cartItemRepository.findAllByCartIdWithProductItem(cart.getId()))
            .thenReturn(List.of());

        // when
        cartMergeExecutor.execute(
            userId,
            guestQuantityMap
        );

        // then
        ArgumentCaptor<CartItem> cartItemCaptor =
            ArgumentCaptor.forClass(CartItem.class);

        verify(cartItemRepository).save(cartItemCaptor.capture());

        CartItem savedCartItem = cartItemCaptor.getValue();

        assertThat(savedCartItem.getCart()).isSameAs(cart);
        assertThat(savedCartItem.getProductItem()).isSameAs(productItem);
        assertThat(savedCartItem.getQuantity()).isEqualTo(4);
    }

    @Test
    @DisplayName("merge 제외 - STOP 상품은 merge하지 않는다")
    void execute_skip_whenProductItemIsStop() {
        // given
        Long userId = 1L;
        Cart cart = cart(10L);

        ProductItem stopProductItem = productItem(
            101L,
            false,
            null
        );

        Map<Long, Integer> guestQuantityMap = new LinkedHashMap<>();
        guestQuantityMap.put(101L, 2);

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));

        when(productItemRepository.findAllByIdInWithProduct(List.of(101L)))
            .thenReturn(List.of(stopProductItem));

        when(cartItemRepository.findAllByCartIdWithProductItem(cart.getId()))
            .thenReturn(List.of());

        // when
        cartMergeExecutor.execute(
            userId,
            guestQuantityMap
        );

        // then
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("merge 제외 - stock이 0인 상품은 merge하지 않는다")
    void execute_skip_whenStockIsZero() {
        // given
        Long userId = 1L;
        Cart cart = cart(10L);

        ProductItem soldOutProductItem = productItem(
            101L,
            true,
            0L
        );

        Map<Long, Integer> guestQuantityMap = new LinkedHashMap<>();
        guestQuantityMap.put(101L, 2);

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));

        when(productItemRepository.findAllByIdInWithProduct(List.of(101L)))
            .thenReturn(List.of(soldOutProductItem));

        when(cartItemRepository.findAllByCartIdWithProductItem(cart.getId()))
            .thenReturn(List.of());

        // when
        cartMergeExecutor.execute(
            userId,
            guestQuantityMap
        );

        // then
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("merge 제외 - DB 장바구니가 이미 50종이면 신규 상품은 추가하지 않는다")
    void execute_skipNewItem_whenCartAlreadyHas50Items() {
        // given
        Long userId = 1L;
        Cart cart = cart(10L);

        ProductItem newProductItem = productItem(
            999L,
            true,
            10L
        );

        List<CartItem> existingCartItems = createExistingCartItems(50);

        Map<Long, Integer> guestQuantityMap = new LinkedHashMap<>();
        guestQuantityMap.put(999L, 1);

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));

        when(productItemRepository.findAllByIdInWithProduct(List.of(999L)))
            .thenReturn(List.of(newProductItem));

        when(cartItemRepository.findAllByCartIdWithProductItem(cart.getId()))
            .thenReturn(existingCartItems);

        // when
        cartMergeExecutor.execute(
            userId,
            guestQuantityMap
        );

        // then
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("merge 성공 - DB 장바구니 49종이면 Redis 순서상 앞 상품 1개만 신규 추가한다")
    void execute_success_addOnlyFirstItem_whenCartHas49Items() {
        // given
        Long userId = 1L;
        Cart cart = cart(10L);

        ProductItem productItem101 = productItem(
            101L,
            true,
            10L
        );

        ProductItem productItem104 = productItem(
            104L,
            true,
            10L
        );

        List<CartItem> existingCartItems = createExistingCartItems(49);

        Map<Long, Integer> guestQuantityMap = new LinkedHashMap<>();
        guestQuantityMap.put(101L, 1);
        guestQuantityMap.put(104L, 1);

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));

        when(productItemRepository.findAllByIdInWithProduct(List.of(101L, 104L)))
            .thenReturn(List.of(productItem101, productItem104));

        when(cartItemRepository.findAllByCartIdWithProductItem(cart.getId()))
            .thenReturn(existingCartItems);

        // when
        cartMergeExecutor.execute(
            userId,
            guestQuantityMap
        );

        // then
        ArgumentCaptor<CartItem> cartItemCaptor =
            ArgumentCaptor.forClass(CartItem.class);

        verify(cartItemRepository).save(cartItemCaptor.capture());

        CartItem savedCartItem = cartItemCaptor.getValue();

        assertThat(savedCartItem.getProductItem()).isSameAs(productItem101);
        assertThat(savedCartItem.getQuantity()).isEqualTo(1);
    }

    private Cart cart(Long cartId) {
        Cart cart = org.mockito.Mockito.mock(Cart.class);
        when(cart.getId()).thenReturn(cartId);
        return cart;
    }

    private User user() {
        return org.mockito.Mockito.mock(User.class);
    }

    private ProductItem productItem(
        Long itemId,
        boolean onSale,
        Long stock
    ) {
        ProductItem productItem = org.mockito.Mockito.mock(ProductItem.class);

        when(productItem.getId()).thenReturn(itemId);
        when(productItem.isOnSale()).thenReturn(onSale);

        // STOP 상품 테스트처럼 isOnSale=false면 getStock()은 호출되지 않으므로
        // stock이 null이 아닐 때만 필요한 테스트에서 명시적으로 사용
        if (stock != null) {
            when(productItem.getStock()).thenReturn(stock);
        }

        return productItem;
    }

    private ProductItem productItemOnlyId(Long itemId) {
        ProductItem productItem = org.mockito.Mockito.mock(ProductItem.class);

        when(productItem.getId()).thenReturn(itemId);

        return productItem;
    }

    private CartItem existingCartItem(
        ProductItem productItem,
        int quantity
    ) {
        CartItem cartItem = org.mockito.Mockito.mock(CartItem.class);

        when(cartItem.getProductItem()).thenReturn(productItem);
        when(cartItem.getQuantity()).thenReturn(quantity);

        return cartItem;
    }

    private CartItem existingCartItemOnlyProductItem(ProductItem productItem) {
        CartItem cartItem = org.mockito.Mockito.mock(CartItem.class);

        when(cartItem.getProductItem()).thenReturn(productItem);

        return cartItem;
    }

    private List<CartItem> createExistingCartItems(int count) {
        return IntStream.rangeClosed(1, count)
            .mapToObj(index -> {
                ProductItem productItem = productItemOnlyId((long) index);

                return existingCartItemOnlyProductItem(productItem);
            })
            .toList();
    }

}
