package com.sparta.one_stop.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShoppingAssistantRequest(
    @NotBlank @Size(max = 500) String message
) {}
