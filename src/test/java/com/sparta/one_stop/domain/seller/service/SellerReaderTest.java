package com.sparta.one_stop.domain.seller.service;

import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class SellerReaderTest {

    @Mock SellerRepository sellerRepository;
    @InjectMocks SellerReader sellerReader;

    @Test
    void 승인되지_않은_판매자는_승인전용_기능을_사용할_수_없다() {
        Seller seller = mock(Seller.class);
        given(sellerRepository.findByUserId(1L)).willReturn(Optional.of(seller));
        given(seller.isApproved()).willReturn(false);

        assertThatThrownBy(() -> sellerReader.getApprovedSeller(1L))
            .isInstanceOf(CustomException.class)
            .satisfies(e -> org.assertj.core.api.Assertions.assertThat(
                ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.SELLER_003));
    }

    @Test
    void 판매자_정보가_없으면_찾을수없음_예외가_발생한다() {
        given(sellerRepository.findByUserId(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sellerReader.getSeller(1L))
            .isInstanceOf(CustomException.class)
            .satisfies(e -> org.assertj.core.api.Assertions.assertThat(
                ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.SELLER_001));
    }
}
