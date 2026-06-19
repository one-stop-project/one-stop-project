package com.sparta.one_stop.domain.ai.service;

import com.sparta.one_stop.domain.ai.dto.ShoppingAssistantResponse;
import com.sparta.one_stop.domain.ai.tool.ShoppingAssistantTool;
import com.sparta.one_stop.global.ai.logging.AiTokenLogger;
import com.sparta.one_stop.global.ai.prompt.AiPromptProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShoppingAssistantService 테스트")
class ShoppingAssistantServiceTest {

    @Mock private ChatClient chatClient;
    @Mock private AiPromptProperties promptProperties;
    @Mock private AiTokenLogger tokenLogger;
    @Mock private ShoppingAssistantTool shoppingAssistantTool;
    @InjectMocks private ShoppingAssistantService shoppingAssistantService;

    @Test
    @DisplayName("Circuit Breaker fallback → 고정 안내 문구 반환")
    void fallback_returns_fixed_message() {
        ShoppingAssistantResponse response = shoppingAssistantService.assistFallback(
            "검은색 가방", null, new RuntimeException("HTTP 400"));

        assertThat(response.answer())
            .isEqualTo("현재 AI 어시스턴트를 이용할 수 없습니다. 잠시 후 다시 시도해주세요.");
    }
}
