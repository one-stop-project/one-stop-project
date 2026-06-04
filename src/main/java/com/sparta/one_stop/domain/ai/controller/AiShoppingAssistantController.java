package com.sparta.one_stop.domain.ai.controller;

import com.sparta.one_stop.domain.ai.dto.ShoppingAssistantRequest;
import com.sparta.one_stop.domain.ai.dto.ShoppingAssistantResponse;
import com.sparta.one_stop.domain.ai.service.ShoppingAssistantService;
import com.sparta.one_stop.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI - Shopping Assistant", description = "AI 쇼핑 어시스턴트 API")
@RestController
@RequestMapping("/api/ai/assistant")
@RequiredArgsConstructor
public class AiShoppingAssistantController {

    private final ShoppingAssistantService shoppingAssistantService;

    @Operation(summary = "AI 쇼핑 어시스턴트 (자연어 상품 추천)")
    @PostMapping
    public ResponseEntity<ApiResponse<ShoppingAssistantResponse>> assist(
        @RequestBody @Valid ShoppingAssistantRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            shoppingAssistantService.assist(request.message(), request.categoryId())
        ));
    }
}
