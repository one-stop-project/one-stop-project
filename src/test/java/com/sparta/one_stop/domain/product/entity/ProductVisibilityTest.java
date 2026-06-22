package com.sparta.one_stop.domain.product.entity;

import com.sparta.one_stop.domain.user.entity.Seller;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ProductVisibilityTest {

    @Test
    void productIsHiddenWhenSellerUserIsMissing() {
        Seller seller = Seller.builder()
            .shopName("shop")
            .businessNumber("1234567890")
            .build();
        seller.approve();
        Product product = Product.builder().seller(seller).name("product").build();
        product.approve();
        ProductItem item = ProductItem.builder().price(1_000L).stock(1L).build();
        ReflectionTestUtils.setField(item, "status",
            com.sparta.one_stop.global.enums.product.ProductItemStatus.ON_SALE);
        product.getProductItems().add(item);

        assertThat(product.isVisibleOnSale()).isFalse();
    }
}
