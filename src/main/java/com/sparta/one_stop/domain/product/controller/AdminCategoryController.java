package com.sparta.one_stop.domain.product.controller;

import com.sparta.one_stop.domain.product.dto.request.CategoryCreateRequest;
import com.sparta.one_stop.domain.product.dto.request.CategoryUpdateRequest;
import com.sparta.one_stop.domain.product.dto.response.CategoryResponse;
import com.sparta.one_stop.domain.product.service.CategoryService;
import com.sparta.one_stop.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Category", description = "관리자 카테고리 관리 API")
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    // TODO: @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')") 추가 예정
    // 현재 권한 제어는 SecurityConfig URL 패턴

    private final CategoryService categoryService;

    // 카테고리 생성
    @Operation(summary = "카테고리 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
        @Valid @RequestBody CategoryCreateRequest request
    ) {
        CategoryResponse response = categoryService.create(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    // 카테고리 이름 수정
    @Operation(summary = "카테고리 이름 수정")
    @PatchMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
        @PathVariable Long categoryId,
        @Valid @RequestBody CategoryUpdateRequest request
    ) {
        CategoryResponse response = categoryService.updateName(categoryId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 카테고리 삭제 (하위 포함 일괄 삭제)
    @Operation(summary = "카테고리 삭제")
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<Void>> delete(
        @PathVariable Long categoryId
    ) {
        categoryService.delete(categoryId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
