package com.sparta.one_stop.domain.ai.controller;

import com.sparta.one_stop.domain.ai.dto.AiReviewSummaryResponse;
import com.sparta.one_stop.domain.ai.service.AiReviewSummaryService;
import com.sparta.one_stop.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI - Review Summary", description = "AI 리뷰 요약 API")
@RestController
@RequestMapping("/api/products/{productId}/reviews")
@RequiredArgsConstructor
public class AiReviewSummaryController {

    private final AiReviewSummaryService aiReviewSummaryService;

    @Operation(summary = "AI 리뷰 요약 조회 (리뷰 5개 이상 시 제공)")
    @GetMapping("/ai-summary")
    public ResponseEntity<ApiResponse<AiReviewSummaryResponse>> getSummary(
        @PathVariable Long productId
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiReviewSummaryService.getSummary(productId)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "AI 리뷰 요약 강제 갱신 (관리자 트리거)")
    @PostMapping("/ai-summary/refresh")
    public ResponseEntity<ApiResponse<AiReviewSummaryResponse>> refreshSummary(
        @PathVariable Long productId
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiReviewSummaryService.refreshSummary(productId)));
    }
}
