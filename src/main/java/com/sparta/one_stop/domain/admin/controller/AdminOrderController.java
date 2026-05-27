package com.sparta.one_stop.domain.admin.controller;

import com.sparta.one_stop.domain.admin.dto.AdminOrderResponse;
import com.sparta.one_stop.domain.admin.service.AdminOrderService;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Admin - Order", description = "관리자 전체 주문 현황 API")
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    // TODO: M3 완료 후 @PreAuthorize("hasRole('ADMIN')") 또는
    //       @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')") 추가 예정
    // 현재는 SecurityConfig URL 패턴으로 ADMIN 권한 제어 중

    private final AdminOrderService adminOrderService;

    @Operation(summary = "전체 주문 현황 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminOrderResponse>>> getOrders(
        // 주문 상태 필터 (선택)
        @RequestParam(required = false) OrderStatus status,
        // 조회 시작일 (선택)
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        // 조회 종료일 (선택)
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        // 구매자 이름 또는 이메일 검색 (선택)
        @RequestParam(required = false) String keyword,
        // 기본 20개, 최신순 정렬
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
            ApiResponse.success(adminOrderService.getOrders(status, from, to, keyword, pageable))
        );
    }
}
