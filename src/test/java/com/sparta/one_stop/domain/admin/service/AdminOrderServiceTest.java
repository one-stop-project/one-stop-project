package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminOrderService 테스트")
class AdminOrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @InjectMocks private AdminOrderService adminOrderService;

    // ─────────────────────────────────────────────────────────────
    //  LIKE 이스케이프
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("LIKE 특수문자 이스케이프")
    class LikeEscape {

        @Test
        @DisplayName("% 포함 키워드 → \\% 이스케이프 처리")
        void escape_percent_in_keyword() {
            given(orderRepository.searchAllOrders(any(), any(), any(), any(), any()))
                .willReturn(Page.empty());
            given(orderRepository.countItemsByOrderIds(any())).willReturn(List.of());

            adminOrderService.getOrders(null, null, null, "100%", Pageable.unpaged());

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(orderRepository).searchAllOrders(any(), any(), any(), captor.capture(), any());
            assertThat(captor.getValue()).isEqualTo("100\\%");
        }

        @Test
        @DisplayName("_ 포함 키워드 → \\_ 이스케이프 처리")
        void escape_underscore_in_keyword() {
            given(orderRepository.searchAllOrders(any(), any(), any(), any(), any()))
                .willReturn(Page.empty());
            given(orderRepository.countItemsByOrderIds(any())).willReturn(List.of());

            adminOrderService.getOrders(null, null, null, "user_01", Pageable.unpaged());

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(orderRepository).searchAllOrders(any(), any(), any(), captor.capture(), any());
            assertThat(captor.getValue()).isEqualTo("user\\_01");
        }

        @Test
        @DisplayName("\\ 포함 키워드 → \\\\ 이스케이프 처리")
        void escape_backslash_in_keyword() {
            given(orderRepository.searchAllOrders(any(), any(), any(), any(), any()))
                .willReturn(Page.empty());
            given(orderRepository.countItemsByOrderIds(any())).willReturn(List.of());

            adminOrderService.getOrders(null, null, null, "a\\b", Pageable.unpaged());

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(orderRepository).searchAllOrders(any(), any(), any(), captor.capture(), any());
            assertThat(captor.getValue()).isEqualTo("a\\\\b");
        }

        @Test
        @DisplayName("빈 키워드 → null 로 변환되어 전체 조회")
        void blank_keyword_becomes_null() {
            given(orderRepository.searchAllOrders(any(), any(), any(), isNull(), any()))
                .willReturn(Page.empty());
            given(orderRepository.countItemsByOrderIds(any())).willReturn(List.of());

            adminOrderService.getOrders(null, null, null, "   ", Pageable.unpaged());

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(orderRepository).searchAllOrders(any(), any(), any(), captor.capture(), any());
            assertThat(captor.getValue()).isNull();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  날짜 범위 검증
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("날짜 범위 검증")
    class DateRange {

        @Test
        @DisplayName("from > to → COMMON_001 예외")
        void from_after_to_throws() {
            LocalDate from = LocalDate.of(2024, 5, 10);
            LocalDate to = LocalDate.of(2024, 5, 1);

            assertThatThrownBy(() ->
                adminOrderService.getOrders(null, from, to, null, Pageable.unpaged()))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.COMMON_001));
        }

        @Test
        @DisplayName("from == to → 정상 처리")
        void from_equals_to_succeeds() {
            LocalDate date = LocalDate.of(2024, 5, 10);
            given(orderRepository.searchAllOrders(any(), any(), any(), any(), any()))
                .willReturn(Page.empty());
            given(orderRepository.countItemsByOrderIds(any())).willReturn(List.of());

            adminOrderService.getOrders(null, date, date, null, Pageable.unpaged());

            verify(orderRepository).searchAllOrders(any(), any(), any(), any(), any());
        }
    }
}
