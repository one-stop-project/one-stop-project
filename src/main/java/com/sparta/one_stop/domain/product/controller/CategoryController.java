package com.sparta.one_stop.domain.product.controller;

import com.sparta.one_stop.domain.product.dto.response.CategoryTreeResponse;
import com.sparta.one_stop.domain.product.service.CategoryService;
import com.sparta.one_stop.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Category", description = "카테고리 조회 API")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // 카테고리 트리 조회
    @Operation(summary = "카테고리 트리 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryTreeResponse>>> getTree() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getTree()));
    }
}
