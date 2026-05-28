package com.sparta.one_stop.domain.cart.entity;

import com.sparta.one_stop.domain.product.entity.ProductItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class CartItemTest {

    @Mock
    private Cart cart;

    @Mock
    private ProductItem productItem;

    @Test
    @DisplayName("CartItem 생성 성공")
    void createCartItem_success() {
        // given
        int quantity = 3;

        // when
        CartItem cartItem = new CartItem(
            cart,
            productItem,
            quantity
        );

        // then
        assertThat(cartItem.getCart()).isSameAs(cart);
        assertThat(cartItem.getProductItem()).isSameAs(productItem);
        assertThat(cartItem.getQuantity()).isEqualTo(quantity);
    }

    @Test
    @DisplayName("CartItem 생성 실패 - cart가 null이면 예외 발생")
    void createCartItem_fail_whenCartIsNull() {
        // given
        Cart nullCart = null;
        int quantity = 1;

        // when & then
        assertThatThrownBy(() -> new CartItem(
            nullCart,
            productItem,
            quantity
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("장바구니 정보는 필수입니다.");
    }

    @Test
    @DisplayName("CartItem 생성 실패 - productItem이 null이면 예외 발생")
    void createCartItem_fail_whenProductItemIsNull() {
        // given
        ProductItem nullProductItem = null;
        int quantity = 1;

        // when & then
        assertThatThrownBy(() -> new CartItem(
            cart,
            nullProductItem,
            quantity
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("상품 옵션 정보는 필수입니다.");
    }

    @Test
    @DisplayName("CartItem 생성 실패 - quantity가 0이면 예외 발생")
    void createCartItem_fail_whenQuantityIsZero() {
        // given
        int quantity = 0;

        // when & then
        assertThatThrownBy(() -> new CartItem(
            cart,
            productItem,
            quantity
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("수량은 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("CartItem 생성 실패 - quantity가 100이면 예외 발생")
    void createCartItem_fail_whenQuantityIsOverMax() {
        // given
        int quantity = 100;

        // when & then
        assertThatThrownBy(() -> new CartItem(
            cart,
            productItem,
            quantity
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("장바구니 최대 수량은 99개입니다.");
    }

    @Test
    @DisplayName("CartItem 생성 실패 - quantity가 음수이면 예외 발생")
    void createCartItem_fail_whenQuantityIsNegative() {
        // given
        int quantity = -1;

        // when & then
        assertThatThrownBy(() -> new CartItem(
            cart,
            productItem,
            quantity
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("수량은 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("increaseQuantity 성공")
    void increaseQuantity_success() {
        // given
        CartItem cartItem = new CartItem(
            cart,
            productItem,
            3
        );

        int increaseQuantity = 2;

        // when
        cartItem.increaseQuantity(increaseQuantity);

        // then
        assertThat(cartItem.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("increaseQuantity 실패 - 증가 수량이 0이면 예외 발생")
    void increaseQuantity_fail_whenQuantityIsZero() {
        // given
        CartItem cartItem = new CartItem(
            cart,
            productItem,
            3
        );

        int increaseQuantity = 0;

        // when & then
        assertThatThrownBy(() -> cartItem.increaseQuantity(increaseQuantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("수량은 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("increaseQuantity 실패 - 증가 수량이 음수이면 예외 발생")
    void increaseQuantity_fail_whenQuantityIsNegative() {
        // given
        CartItem cartItem = new CartItem(
            cart,
            productItem,
            3
        );

        int increaseQuantity = -1;

        // when & then
        assertThatThrownBy(() -> cartItem.increaseQuantity(increaseQuantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("수량은 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("increaseQuantity 실패 - 증가 후 수량이 99개를 초과하면 예외 발생")
    void increaseQuantity_fail_whenResultQuantityIsOverMax() {
        // given
        CartItem cartItem = new CartItem(
            cart,
            productItem,
            99
        );

        int increaseQuantity = 1;

        // when & then
        assertThatThrownBy(() -> cartItem.increaseQuantity(increaseQuantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("장바구니 최대 수량은 99개입니다.");
    }

    @Test
    @DisplayName("decreaseQuantity 성공")
    void decreaseQuantity_success() {
        // given
        CartItem cartItem = new CartItem(
            cart,
            productItem,
            5
        );

        int decreaseQuantity = 2;

        // when
        cartItem.decreaseQuantity(decreaseQuantity);

        // then
        assertThat(cartItem.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("decreaseQuantity 실패 - 감소 수량이 0이면 예외 발생")
    void decreaseQuantity_fail_whenQuantityIsZero() {
        // given
        CartItem cartItem = new CartItem(
            cart,
            productItem,
            5
        );

        int decreaseQuantity = 0;

        // when & then
        assertThatThrownBy(() -> cartItem.decreaseQuantity(decreaseQuantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("수량은 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("decreaseQuantity 실패 - 감소 수량이 음수이면 예외 발생")
    void decreaseQuantity_fail_whenQuantityIsNegative() {
        // given
        CartItem cartItem = new CartItem(
            cart,
            productItem,
            5
        );

        int decreaseQuantity = -1;

        // when & then
        assertThatThrownBy(() -> cartItem.decreaseQuantity(decreaseQuantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("수량은 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("decreaseQuantity 실패 - 감소 후 수량이 1 미만이면 예외 발생")
    void decreaseQuantity_fail_whenResultQuantityIsLessThanOne() {
        // given
        CartItem cartItem = new CartItem(
            cart,
            productItem,
            5
        );

        int decreaseQuantity = 5;

        // when & then
        assertThatThrownBy(() -> cartItem.decreaseQuantity(decreaseQuantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("수량은 1 미만이 될 수 없습니다.");
    }

    @Test
    @DisplayName("changeQuantity 성공")
    void changeQuantity_success() {
        // given
        CartItem cartItem = new CartItem(
            cart,
            productItem,
            3
        );

        int changeQuantity = 10;

        // when
        cartItem.changeQuantity(changeQuantity);

        // then
        assertThat(cartItem.getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("changeQuantity 실패 - 변경 수량이 0이면 예외 발생")
    void changeQuantity_fail_whenQuantityIsZero() {
        // given
        CartItem cartItem = new CartItem(
            cart,
            productItem,
            3
        );

        int changeQuantity = 0;

        // when & then
        assertThatThrownBy(() -> cartItem.changeQuantity(changeQuantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("수량은 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("changeQuantity 실패 - 변경 수량이 음수이면 예외 발생")
    void changeQuantity_fail_whenQuantityIsNegative() {
        // given
        CartItem cartItem = new CartItem(
            cart,
            productItem,
            3
        );

        int changeQuantity = -1;

        // when & then
        assertThatThrownBy(() -> cartItem.changeQuantity(changeQuantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("수량은 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("changeQuantity 실패 - 변경 수량이 100이면 예외 발생")
    void changeQuantity_fail_whenQuantityIsOverMax() {
        // given
        CartItem cartItem = new CartItem(
            cart,
            productItem,
            3
        );

        int changeQuantity = 100;

        // when & then
        assertThatThrownBy(() -> cartItem.changeQuantity(changeQuantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("장바구니 최대 수량은 99개입니다.");
    }

}
