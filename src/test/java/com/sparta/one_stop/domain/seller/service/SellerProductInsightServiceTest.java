package com.sparta.one_stop.domain.seller.service;

import com.sparta.one_stop.domain.admin.repository.AdminActionHistoryRepository;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.enums.admin.AdminActionTarget;
import com.sparta.one_stop.global.enums.admin.AdminActionType;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class SellerProductInsightServiceTest {

    @Mock SellerReader sellerReader;
    @Mock ProductRepository productRepository;
    @Mock AdminActionHistoryRepository adminActionHistoryRepository;
    @InjectMocks SellerProductInsightService service;

    @Test
    void 반려_상품의_반려_이력이_없으면_데이터_불일치_예외가_발생한다() {
        Seller seller = mock(Seller.class);
        Product product = mock(Product.class);
        given(seller.getId()).willReturn(10L);
        given(product.getSeller()).willReturn(seller);
        given(product.getStatus()).willReturn(ProductStatus.REJECTED);
        given(sellerReader.getApprovedSeller(1L)).willReturn(seller);
        given(productRepository.findById(100L)).willReturn(Optional.of(product));
        given(adminActionHistoryRepository
            .findTopByTargetTypeAndTargetIdAndActionOrderByCreatedAtDesc(
                AdminActionTarget.PRODUCT, 100L, AdminActionType.REJECT))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRejectReason(1L, 100L))
            .isInstanceOf(CustomException.class)
            .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_017));
    }
}
