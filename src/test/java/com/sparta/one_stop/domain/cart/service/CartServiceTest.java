package com.sparta.one_stop.domain.cart.service;

import com.sparta.one_stop.domain.cart.dto.request.AddCartItemRequest;
import com.sparta.one_stop.domain.cart.dto.request.UpdateCartItemRequest;
import com.sparta.one_stop.domain.cart.dto.response.CartItemResponse;
import com.sparta.one_stop.domain.cart.dto.response.CartPageResponse;
import com.sparta.one_stop.domain.cart.dto.response.UpdateCartItemResponse;
import com.sparta.one_stop.domain.cart.entity.Cart;
import com.sparta.one_stop.domain.cart.entity.CartItem;
import com.sparta.one_stop.domain.cart.repository.CartItemRepository;
import com.sparta.one_stop.domain.cart.repository.CartRepository;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductItemRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.security.AuthUser;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductItemRepository productItemRepository;

    @Mock
    private GuestCartService guestCartService;

    @Mock
    private AuthUser authUser;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private CartService cartService;

    @Test
    @DisplayName("비로그인 장바구니 담기 - GuestCartService로 위임한다")
    void addCartItem_delegateToGuestCartService_whenAuthUserIsNull() {
        // given
        String guestCartId = "guest-id";
        AddCartItemRequest request = new AddCartItemRequest(
            101L,
            2
        );

        CartItemResponse expectedResponse = CartItemResponse.ofGuest(
            101L,
            2
        );

        when(guestCartService.addCartItem(
            guestCartId,
            response,
            request
        )).thenReturn(expectedResponse);

        // when
        CartItemResponse result = cartService.addCartItem(
            null,
            guestCartId,
            response,
            request
        );

        // then
        assertThat(result).isSameAs(expectedResponse);

        verify(guestCartService).addCartItem(
            guestCartId,
            response,
            request
        );

        verify(userRepository, never()).findById(any());
        verify(productItemRepository, never()).findById(any());
    }

    @Test
    @DisplayName("비로그인 장바구니 조회 - GuestCartService로 위임한다")
    void getCart_delegateToGuestCartService_whenAuthUserIsNull() {
        // given
        String guestCartId = "guest-id";
        Pageable pageable = PageRequest.of(
            0,
            20
        );

        CartPageResponse expectedResponse = CartPageResponse.empty(
            0,
            20
        );

        when(guestCartService.getCart(
            guestCartId,
            response,
            pageable
        )).thenReturn(expectedResponse);

        // when
        CartPageResponse result = cartService.getCart(
            null,
            guestCartId,
            response,
            pageable
        );

        // then
        assertThat(result).isSameAs(expectedResponse);

        verify(guestCartService).getCart(
            guestCartId,
            response,
            pageable
        );

        verify(cartRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("비로그인 장바구니 수량 변경 - GuestCartService로 위임한다")
    void updateCartItemQuantity_delegateToGuestCartService_whenAuthUserIsNull() {
        // given
        String guestCartId = "guest-id";
        Long itemId = 101L;
        UpdateCartItemRequest request = new UpdateCartItemRequest(3);

        UpdateCartItemResponse expectedResponse = UpdateCartItemResponse.of(
            itemId,
            3
        );

        when(guestCartService.updateCartItemQuantity(
            guestCartId,
            response,
            itemId,
            request
        )).thenReturn(expectedResponse);

        // when
        UpdateCartItemResponse result = cartService.updateCartItemQuantity(
            null,
            guestCartId,
            response,
            itemId,
            request
        );

        // then
        assertThat(result).isSameAs(expectedResponse);

        verify(guestCartService).updateCartItemQuantity(
            guestCartId,
            response,
            itemId,
            request
        );

        verify(cartRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("비로그인 장바구니 삭제 - GuestCartService로 위임한다")
    void deleteCartItem_delegateToGuestCartService_whenAuthUserIsNull() {
        // given
        String guestCartId = "guest-id";
        Long itemId = 101L;

        // when
        cartService.deleteCartItem(
            null,
            guestCartId,
            response,
            itemId
        );

        // then
        verify(guestCartService).deleteCartItem(
            guestCartId,
            response,
            itemId
        );

        verify(cartRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("로그인 장바구니 담기 성공 - 신규 상품을 CartItem으로 저장한다")
    void addCartItem_success_saveNewCartItem_whenLoginUser() {
        // given
        Long userId = 1L;
        Long sellerUserId = 2L;
        Long itemId = 101L;

        AddCartItemRequest request = new AddCartItemRequest(
            itemId,
            2
        );

        User user = user();
        Cart cart = cart(10L);
        ProductItem productItem = productItem(
            itemId,
            true,
            10L,
            sellerUserId
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(productItemRepository.findById(itemId))
            .thenReturn(Optional.of(productItem));
        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductItemId(
            cart.getId(),
            itemId
        )).thenReturn(Optional.empty());
        when(cartItemRepository.countByCartId(cart.getId()))
            .thenReturn(0L);

        // when
        CartItemResponse result = cartService.addCartItem(
            userId,
            request
        );

        // then
        ArgumentCaptor<CartItem> cartItemCaptor =
            ArgumentCaptor.forClass(CartItem.class);

        verify(cartItemRepository).save(cartItemCaptor.capture());

        CartItem savedCartItem = cartItemCaptor.getValue();

        assertThat(savedCartItem.getCart()).isSameAs(cart);
        assertThat(savedCartItem.getProductItem()).isSameAs(productItem);
        assertThat(savedCartItem.getQuantity()).isEqualTo(2);

        assertThat(result.itemId()).isEqualTo(itemId);
        assertThat(result.quantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("로그인 장바구니 담기 성공 - 기존 상품이면 수량을 증가시킨다")
    void addCartItem_success_increaseQuantity_whenCartItemAlreadyExists() {
        // given
        Long userId = 1L;
        Long sellerUserId = 2L;
        Long itemId = 101L;

        AddCartItemRequest request = new AddCartItemRequest(
            itemId,
            2
        );

        User user = user();
        Cart cart = cart(10L);

        ProductItem productItem = productItem(
            itemId,
            true,
            10L,
            sellerUserId
        );

        CartItem existingCartItem = new CartItem(
            cart,
            productItem,
            3
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(productItemRepository.findById(itemId))
            .thenReturn(Optional.of(productItem));
        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductItemId(
            cart.getId(),
            itemId
        )).thenReturn(Optional.of(existingCartItem));

        // when
        CartItemResponse result = cartService.addCartItem(
            userId,
            request
        );

        // then
        verify(cartItemRepository, never()).save(any(CartItem.class));
        verify(cartItemRepository, never()).countByCartId(any());

        assertThat(existingCartItem.getQuantity()).isEqualTo(5);
        assertThat(result.itemId()).isEqualTo(itemId);
        assertThat(result.quantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("로그인 장바구니 담기 성공 - 장바구니가 없으면 새 Cart를 자동 생성한다")
    void addCartItem_success_createCartAutomatically_whenCartDoesNotExist() {
        // given
        Long userId = 1L;
        Long sellerUserId = 2L;
        Long itemId = 101L;

        AddCartItemRequest request = new AddCartItemRequest(
            itemId,
            2
        );

        User user = user();
        Cart savedCart = cart(10L);

        ProductItem productItem = productItem(
            itemId,
            true,
            10L,
            sellerUserId
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(productItemRepository.findById(itemId))
            .thenReturn(Optional.of(productItem));
        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class)))
            .thenReturn(savedCart);
        when(cartItemRepository.findByCartIdAndProductItemId(
            savedCart.getId(),
            itemId
        )).thenReturn(Optional.empty());
        when(cartItemRepository.countByCartId(savedCart.getId()))
            .thenReturn(0L);

        // when
        CartItemResponse result = cartService.addCartItem(
            userId,
            request
        );

        // then
        ArgumentCaptor<Cart> cartCaptor =
            ArgumentCaptor.forClass(Cart.class);

        verify(cartRepository).save(cartCaptor.capture());

        Cart newCart = cartCaptor.getValue();

        assertThat(newCart.getUser()).isSameAs(user);

        ArgumentCaptor<CartItem> cartItemCaptor =
            ArgumentCaptor.forClass(CartItem.class);

        verify(cartItemRepository).save(cartItemCaptor.capture());

        CartItem savedCartItem = cartItemCaptor.getValue();

        assertThat(savedCartItem.getCart()).isSameAs(savedCart);
        assertThat(savedCartItem.getProductItem()).isSameAs(productItem);
        assertThat(savedCartItem.getQuantity()).isEqualTo(2);

        assertThat(result.itemId()).isEqualTo(itemId);
        assertThat(result.quantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("로그인 장바구니 담기 실패 - 본인 상품은 담을 수 없다")
    void addCartItem_fail_whenOwnProduct() {
        // given
        Long userId = 1L;
        Long sellerUserId = 1L;
        Long itemId = 101L;

        AddCartItemRequest request = new AddCartItemRequest(
            itemId,
            1
        );

        User user = user();

        ProductItem productItem = productItemWithSellerUserId(sellerUserId);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(productItemRepository.findById(itemId))
            .thenReturn(Optional.of(productItem));

        // when & then
        assertThatThrownBy(() -> cartService.addCartItem(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .hasMessage("본인의 상품은 장바구니에 담을 수 없습니다");

        verify(cartRepository, never()).findByUserId(any());
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("로그인 장바구니 담기 실패 - STOP 상품은 담을 수 없다")
    void addCartItem_fail_whenProductItemIsStop() {
        // given
        Long userId = 1L;
        Long sellerUserId = 2L;
        Long itemId = 101L;

        AddCartItemRequest request = new AddCartItemRequest(
            itemId,
            1
        );

        User user = user();

        ProductItem productItem = productItemForAddValidation(
            false,
            null,
            sellerUserId
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(productItemRepository.findById(itemId))
            .thenReturn(Optional.of(productItem));

        // when & then
        assertThatThrownBy(() -> cartService.addCartItem(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .hasMessage("장바구니에 담을 수 없는 상품입니다");

        verify(cartRepository, never()).findByUserId(any());
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("로그인 장바구니 담기 실패 - 품절 상품은 담을 수 없다")
    void addCartItem_fail_whenStockIsZero() {
        // given
        Long userId = 1L;
        Long sellerUserId = 2L;
        Long itemId = 101L;

        AddCartItemRequest request = new AddCartItemRequest(
            itemId,
            1
        );

        User user = user();

        ProductItem productItem = productItemForAddValidation(
            true,
            0L,
            sellerUserId
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(productItemRepository.findById(itemId))
            .thenReturn(Optional.of(productItem));

        // when & then
        assertThatThrownBy(() -> cartService.addCartItem(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .hasMessage("재고가 부족합니다");

        verify(cartRepository, never()).findByUserId(any());
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("로그인 장바구니 담기 실패 - 요청 수량이 재고를 초과하면 담을 수 없다")
    void addCartItem_fail_whenQuantityExceedsStock() {
        // given
        Long userId = 1L;
        Long sellerUserId = 2L;
        Long itemId = 101L;

        AddCartItemRequest request = new AddCartItemRequest(
            itemId,
            5
        );

        User user = user();

        ProductItem productItem = productItemForAddValidation(
            true,
            3L,
            sellerUserId
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(productItemRepository.findById(itemId))
            .thenReturn(Optional.of(productItem));

        // when & then
        assertThatThrownBy(() -> cartService.addCartItem(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .hasMessage("재고가 부족합니다");

        verify(cartRepository, never()).findByUserId(any());
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("로그인 장바구니 담기 실패 - 장바구니 상품 종류가 50종이면 신규 상품을 담을 수 없다")
    void addCartItem_fail_whenCartItemCountIsAlready50() {
        // given
        Long userId = 1L;
        Long sellerUserId = 2L;
        Long itemId = 101L;

        AddCartItemRequest request = new AddCartItemRequest(
            itemId,
            1
        );

        User user = user();
        Cart cart = cart(10L);

        ProductItem productItem = productItem(
            itemId,
            true,
            10L,
            sellerUserId
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(productItemRepository.findById(itemId))
            .thenReturn(Optional.of(productItem));
        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductItemId(
            cart.getId(),
            itemId
        )).thenReturn(Optional.empty());
        when(cartItemRepository.countByCartId(cart.getId()))
            .thenReturn(50L);

        // when & then
        assertThatThrownBy(() -> cartService.addCartItem(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .hasMessage("장바구니에 담을 수 있는 상품은 최대 50개입니다");

        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("로그인 장바구니 담기 실패 - 신규 상품 요청 수량이 99개를 초과하면 담을 수 없다")
    void addCartItem_fail_whenRequestQuantityExceedsMaxLimit() {
        // given
        Long userId = 1L;
        Long sellerUserId = 2L;
        Long itemId = 101L;

        AddCartItemRequest request = new AddCartItemRequest(
            itemId,
            100
        );

        User user = user();

        ProductItem productItem = productItemForAddValidation(
            true,
            null,
            sellerUserId
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(productItemRepository.findById(itemId))
            .thenReturn(Optional.of(productItem));

        // when & then
        assertThatThrownBy(() -> cartService.addCartItem(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .hasMessage("수량은 1개 이상 99개 이하여야 합니다");

        verify(cartRepository, never()).findByUserId(any());
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("로그인 장바구니 담기 실패 - 기존 상품 수량 증가 후 99개를 초과하면 담을 수 없다")
    void addCartItem_fail_whenIncreasedQuantityExceedsMaxLimit() {
        // given
        Long userId = 1L;
        Long sellerUserId = 2L;
        Long itemId = 101L;

        AddCartItemRequest request = new AddCartItemRequest(
            itemId,
            10
        );

        User user = user();
        Cart cart = cart(10L);

        ProductItem productItem = productItem(
            itemId,
            true,
            200L,
            sellerUserId
        );

        CartItem existingCartItem = new CartItem(
            cart,
            productItem,
            90
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(productItemRepository.findById(itemId))
            .thenReturn(Optional.of(productItem));
        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductItemId(
            cart.getId(),
            itemId
        )).thenReturn(Optional.of(existingCartItem));

        // when & then
        assertThatThrownBy(() -> cartService.addCartItem(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .hasMessage("수량은 1개 이상 99개 이하여야 합니다");

        verify(cartItemRepository, never()).save(any(CartItem.class));
        verify(cartItemRepository, never()).countByCartId(any());

        assertThat(existingCartItem.getQuantity()).isEqualTo(90);
    }

    @Test
    @DisplayName("로그인 장바구니 조회 성공 - 장바구니가 없으면 빈 장바구니를 반환한다")
    void getCart_success_returnEmptyCart_whenCartDoesNotExist() {
        // given
        Long userId = 1L;
        Pageable pageable = PageRequest.of(
            0,
            20
        );

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.empty());

        // when
        CartPageResponse result = cartService.getCart(
            userId,
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
    }

    @Test
    @DisplayName("로그인 장바구니 조회 성공 - 기존 장바구니를 페이징 조회한다")
    void getCart_success_whenCartExists() {
        // given
        Long userId = 1L;
        Pageable pageable = PageRequest.of(
            0,
            20
        );

        Cart cart = cart(10L);
        ProductItem productItem = productItemForDetail(
            101L,
            true,
            10L
        );

        CartItem cartItem = cartItem(
            100L,
            productItem,
            2
        );

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));
        when(cartItemRepository.findPageByCartIdWithProduct(
            cart.getId(),
            pageable
        )).thenReturn(new PageImpl<>(
            List.of(cartItem),
            pageable,
            1
        ));
        when(cartItemRepository.sumTotalPriceByCartId(cart.getId()))
            .thenReturn(2000L);
        when(cartItemRepository.sumQuantityByCartId(cart.getId()))
            .thenReturn(2L);

        // when
        CartPageResponse result = cartService.getCart(
            userId,
            pageable
        );

        // then
        assertThat(result.cartId()).isEqualTo(cart.getId());
        assertThat(result.content()).hasSize(1);
        assertThat(result.totalPrice()).isEqualTo(2000L);
        assertThat(result.itemCount()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("로그인 장바구니 수량 변경 성공 - 최종 수량이 증가하면 증가분만큼 increaseQuantity를 호출한다")
    void updateCartItemQuantityByItemId_success_increaseQuantity() {
        // given
        Long userId = 1L;
        Long itemId = 101L;

        UpdateCartItemRequest request = new UpdateCartItemRequest(5);

        Cart cart = cart(10L);
        ProductItem productItem = productItemForQuantityUpdate(
            true,
            10L
        );

        CartItem cartItem = cartItemForQuantityUpdate(
            productItem,
            3
        );

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductItemId(
            cart.getId(),
            itemId
        )).thenReturn(Optional.of(cartItem));

        // when
        UpdateCartItemResponse result = cartService.updateCartItemQuantityByItemId(
            userId,
            itemId,
            request
        );

        // then
        verify(cartItem).increaseQuantity(2);
        verify(cartItem, never()).decreaseQuantity(anyInt());

        assertThat(result.itemId()).isEqualTo(itemId);
        assertThat(result.quantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("로그인 장바구니 수량 변경 성공 - 최종 수량이 감소하면 감소분만큼 decreaseQuantity를 호출한다")
    void updateCartItemQuantityByItemId_success_decreaseQuantity() {
        // given
        Long userId = 1L;
        Long itemId = 101L;

        UpdateCartItemRequest request = new UpdateCartItemRequest(2);

        Cart cart = cart(10L);
        CartItem cartItem = org.mockito.Mockito.mock(CartItem.class);

        when(cartItem.getQuantity())
            .thenReturn(5);

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductItemId(
            cart.getId(),
            itemId
        )).thenReturn(Optional.of(cartItem));

        // when
        UpdateCartItemResponse result = cartService.updateCartItemQuantityByItemId(
            userId,
            itemId,
            request
        );

        // then
        verify(cartItem).decreaseQuantity(3);
        verify(cartItem, never()).increaseQuantity(anyInt());

        assertThat(result.itemId()).isEqualTo(itemId);
        assertThat(result.quantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("로그인 장바구니 수량 변경 실패 - 최종 수량이 99개를 초과하면 변경할 수 없다")
    void updateCartItemQuantityByItemId_fail_whenRequestQuantityExceedsMaxLimit() {
        // given
        Long userId = 1L;
        Long itemId = 101L;

        UpdateCartItemRequest request = new UpdateCartItemRequest(100);

        Cart cart = cart(10L);

        ProductItem productItem = productItemForQuantityUpdate(
            true,
            null
        );

        CartItem cartItem = cartItemForQuantityUpdate(
            productItem,
            90
        );

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductItemId(
            cart.getId(),
            itemId
        )).thenReturn(Optional.of(cartItem));

        // when & then
        assertThatThrownBy(() -> cartService.updateCartItemQuantityByItemId(
            userId,
            itemId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .hasMessage("수량은 1개 이상 99개 이하여야 합니다");

        verify(cartItem, never()).increaseQuantity(anyInt());
        verify(cartItem, never()).decreaseQuantity(anyInt());
    }

    @Test
    @DisplayName("로그인 장바구니 수량 변경 실패 - 장바구니가 없으면 예외 발생")
    void updateCartItemQuantityByItemId_fail_whenCartDoesNotExist() {
        // given
        Long userId = 1L;
        Long itemId = 101L;

        UpdateCartItemRequest request = new UpdateCartItemRequest(2);

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> cartService.updateCartItemQuantityByItemId(
            userId,
            itemId,
            request
        ))
            .isInstanceOf(CustomException.class);

        verify(cartItemRepository, never()).findByCartIdAndProductItemId(
            any(),
            any()
        );
    }

    @Test
    @DisplayName("로그인 장바구니 수량 변경 실패 - 장바구니 상품이 없으면 예외 발생")
    void updateCartItemQuantityByItemId_fail_whenCartItemDoesNotExist() {
        // given
        Long userId = 1L;
        Long itemId = 101L;

        UpdateCartItemRequest request = new UpdateCartItemRequest(2);

        Cart cart = cart(10L);

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductItemId(
            cart.getId(),
            itemId
        )).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> cartService.updateCartItemQuantityByItemId(
            userId,
            itemId,
            request
        ))
            .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("로그인 장바구니 삭제 성공 - itemId 기준으로 CartItem을 삭제한다")
    void deleteCartItemByItemId_success() {
        // given
        Long userId = 1L;
        Long itemId = 101L;

        Cart cart = cart(10L);
        CartItem cartItem = org.mockito.Mockito.mock(CartItem.class);

        when(cartRepository.findByUserId(userId))
            .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductItemId(
            cart.getId(),
            itemId
        )).thenReturn(Optional.of(cartItem));

        // when
        cartService.deleteCartItemByItemId(
            userId,
            itemId
        );

        // then
        verify(cartItemRepository).delete(cartItem);
    }

    private User user() {
        return org.mockito.Mockito.mock(User.class);
    }

    private Cart cart(Long cartId) {
        Cart cart = org.mockito.Mockito.mock(Cart.class);

        when(cart.getId()).thenReturn(cartId);

        return cart;
    }

    private ProductItem productItem(
        Long itemId,
        boolean onSale,
        Long stock,
        Long sellerUserId
    ) {
        ProductItem productItem = org.mockito.Mockito.mock(ProductItem.class);
        Product product = org.mockito.Mockito.mock(Product.class);
        Seller seller = org.mockito.Mockito.mock(Seller.class);
        User sellerUser = org.mockito.Mockito.mock(User.class);

        when(productItem.getId()).thenReturn(itemId);
        when(productItem.isOnSale()).thenReturn(onSale);

        if (stock != null) {
            when(productItem.getStock()).thenReturn(stock);
        }

        when(productItem.getProduct()).thenReturn(product);
        when(product.getSeller()).thenReturn(seller);
        when(seller.getUser()).thenReturn(sellerUser);
        when(sellerUser.getId()).thenReturn(sellerUserId);

        return productItem;
    }

    private ProductItem productItemOnlyId(Long itemId) {
        ProductItem productItem = org.mockito.Mockito.mock(ProductItem.class);

        when(productItem.getId()).thenReturn(itemId);

        return productItem;
    }

    private ProductItem productItemForDetail(
        Long itemId,
        boolean onSale,
        Long stock
    ) {
        ProductItem productItem = productItemOnlyId(itemId);
        Product product = org.mockito.Mockito.mock(Product.class);

        when(productItem.isOnSale()).thenReturn(onSale);
        when(productItem.getStock()).thenReturn(stock);
        when(productItem.getProduct()).thenReturn(product);
        when(productItem.getOptionSummary()).thenReturn("옵션");
        when(productItem.getPrice()).thenReturn(1000L);

        when(product.getId()).thenReturn(1L);
        when(product.getName()).thenReturn("상품명");
        when(product.getThumbnailUrl()).thenReturn("thumbnail.jpg");

        return productItem;
    }

    private CartItem cartItem(
        Long cartItemId,
        ProductItem productItem,
        int quantity
    ) {
        CartItem cartItem = org.mockito.Mockito.mock(CartItem.class);

        when(cartItem.getId()).thenReturn(cartItemId);
        when(cartItem.getProductItem()).thenReturn(productItem);
        when(cartItem.getQuantity()).thenReturn(quantity);

        return cartItem;
    }

    private CartItem cartItemForQuantityUpdate(
        ProductItem productItem,
        int quantity
    ) {
        CartItem cartItem = org.mockito.Mockito.mock(CartItem.class);

        when(cartItem.getProductItem()).thenReturn(productItem);
        when(cartItem.getQuantity()).thenReturn(quantity);

        return cartItem;
    }

    private ProductItem productItemForQuantityUpdate(
        boolean onSale,
        Long stock
    ) {
        ProductItem productItem = org.mockito.Mockito.mock(ProductItem.class);

        when(productItem.isOnSale()).thenReturn(onSale);

        if (stock != null) {
            when(productItem.getStock()).thenReturn(stock);
        }

        return productItem;
    }

    private ProductItem productItemWithSellerUserId(Long sellerUserId) {
        ProductItem productItem = org.mockito.Mockito.mock(ProductItem.class);
        Product product = org.mockito.Mockito.mock(Product.class);
        Seller seller = org.mockito.Mockito.mock(Seller.class);
        User sellerUser = org.mockito.Mockito.mock(User.class);

        when(productItem.getProduct()).thenReturn(product);
        when(product.getSeller()).thenReturn(seller);
        when(seller.getUser()).thenReturn(sellerUser);
        when(sellerUser.getId()).thenReturn(sellerUserId);

        return productItem;
    }

    private ProductItem productItemForAddValidation(
        boolean onSale,
        Long stock,
        Long sellerUserId
    ) {
        ProductItem productItem = productItemWithSellerUserId(sellerUserId);

        when(productItem.isOnSale()).thenReturn(onSale);

        if (stock != null) {
            when(productItem.getStock()).thenReturn(stock);
        }

        return productItem;
    }

}
