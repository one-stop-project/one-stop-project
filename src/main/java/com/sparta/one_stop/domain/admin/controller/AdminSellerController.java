package com.sparta.one_stop.domain.admin.controller;

import com.sparta.one_stop.domain.admin.dto.SellerResponse;
import com.sparta.one_stop.domain.admin.service.AdminSellerService;
import com.sparta.one_stop.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin - Seller", description = "판매자 승인/반려 관리 API")
@RestController
@RequestMapping("/api/admin/sellers")
@RequiredArgsConstructor
public class AdminSellerController {

    // TODO: M3 완료 후 @PreAuthorize("hasRole('ADMIN')") 또는
    //       @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')") 추가 예정
    // 현재는 SecurityConfig URL 패턴으로 ADMIN 권한 제어 중

    private final AdminSellerService adminSellerService;

    // 대기 중인 판매자 목록 조회
    @Operation(summary = "대기 중인 판매자 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SellerResponse>>> getPendingSellers() {
        List<SellerResponse> response = adminSellerService.getPendingSellers()
            .stream()
            .map(SellerResponse::from)
            .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 판매자 승인
    @Operation(summary = "판매자 승인")
    @PostMapping("/{sellerId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveSeller(@PathVariable Long sellerId) {
        adminSellerService.approveSeller(sellerId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 판매자 반려
    @Operation(summary = "판매자 반려")
    @PostMapping("/{sellerId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectSeller(@PathVariable Long sellerId) {
        adminSellerService.rejectSeller(sellerId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 판매자 강제 비활성화
    @Operation(summary = "판매자 강제 비활성화")
    @PostMapping("/{sellerId}/force-inactive")
    public ResponseEntity<ApiResponse<Void>> forceInactiveSeller(@PathVariable Long sellerId) {
        adminSellerService.forceInactiveSeller(sellerId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
