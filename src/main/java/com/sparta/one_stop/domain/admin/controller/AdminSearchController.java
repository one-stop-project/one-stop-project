package com.sparta.one_stop.domain.admin.controller;

import com.sparta.one_stop.domain.product.dto.response.PopularKeywordAdminResponse;
import com.sparta.one_stop.domain.product.service.PopularKeywordService;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

@Tag(name = "Admin - Search", description = "관리자 인기 검색어 조회 API")
@RestController
@RequestMapping("/api/admin/search")
@RequiredArgsConstructor
public class AdminSearchController {

    private static final int ADMIN_LIMIT_MIN = 1;
    private static final int ADMIN_LIMIT_MAX = 50;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PopularKeywordService popularKeywordService;

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "관리자 인기 검색어 조회 (특정일)")
    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<PopularKeywordAdminResponse>> getPopular(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(defaultValue = "20") int limit
    ) {
        validateLimit(limit);
        LocalDate target = (date != null) ? date : LocalDate.now(KST);
        return ResponseEntity.ok(ApiResponse.success(
            popularKeywordService.getPopularKeywordsByDate(target, limit)
        ));
    }

    private void validateLimit(int limit) {
        if (limit < ADMIN_LIMIT_MIN || limit > ADMIN_LIMIT_MAX) {
            throw new CustomException(ErrorCode.PRODUCT_014,
                "limit은 " + ADMIN_LIMIT_MIN + "~" + ADMIN_LIMIT_MAX + " 범위여야 합니다 (요청 limit=" + limit + ")");
        }
    }
}
