package com.sparta.one_stop.domain.ai.controller;

import com.sparta.one_stop.domain.ai.dto.RelatedProductResponse;
import com.sparta.one_stop.domain.ai.service.AiRelatedProductService;
import com.sparta.one_stop.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AI - Related Product", description = "AI 추천 상품 API")
@RestController
@RequestMapping("/api/products/{productId}")
@RequiredArgsConstructor
public class AiRelatedProductController {

    private final AiRelatedProductService aiRelatedProductService;

    @Operation(summary = "연관 상품 추천 (같은 카테고리 인기순 TOP 10, AI 장애 시 인기상품 Fallback)")
    @GetMapping("/related")
    public ResponseEntity<ApiResponse<List<RelatedProductResponse>>> getRelatedProducts(
        @PathVariable Long productId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            aiRelatedProductService.getRelatedProducts(productId)
        ));
    }
}
