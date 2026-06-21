package com.sparta.one_stop.domain.seller.service;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.seller.repository.SellerReviewQueryRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SellerReviewServiceTest {

    @Mock SellerReader sellerReader;
    @Mock SellerReviewQueryRepository queryRepository;
    @Mock ProductRepository productRepository;
    @Mock SellerPagePolicy pagePolicy;
    @InjectMocks SellerReviewService service;

    @Test
    void 다른_판매자의_상품_리뷰는_조회할_수_없다() {
        Seller loginSeller = mock(Seller.class);
        Seller owner = mock(Seller.class);
        Product product = mock(Product.class);
        var pageable = PageRequest.of(0, 20);
        given(loginSeller.getId()).willReturn(10L);
        given(owner.getId()).willReturn(20L);
        given(product.getSeller()).willReturn(owner);
        given(sellerReader.getApprovedSeller(1L)).willReturn(loginSeller);
        given(productRepository.findById(100L)).willReturn(Optional.of(product));

        assertThatThrownBy(() -> service.getProductReviews(1L, 100L, pageable))
            .isInstanceOf(CustomException.class)
            .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_008));
        verify(pagePolicy).validate(pageable);
    }

    @Test
    void 존재하지_않는_상품은_빈페이지가_아닌_찾을수없음으로_응답한다() {
        Seller seller = mock(Seller.class);
        var pageable = PageRequest.of(0, 20);
        given(sellerReader.getApprovedSeller(1L)).willReturn(seller);
        given(productRepository.findById(100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProductReviews(1L, 100L, pageable))
            .isInstanceOf(CustomException.class)
            .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_001));
    }
}
