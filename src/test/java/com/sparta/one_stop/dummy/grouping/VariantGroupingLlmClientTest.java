package com.sparta.one_stop.dummy.grouping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.dummy.description.DummyPromptProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("VariantGroupingLlmClient")
class VariantGroupingLlmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DummyPromptProperties prompts = new DummyPromptProperties("변형을 묶어라");
    private final Map<String, Object> input = Map.of("clusters", List.of());

    @Test
    @DisplayName("정상 응답 → clusterId별 그룹으로 파싱")
    void parsesGroupsByClusterId() throws Exception {
        String json = """
            {"groups":[
              {"clusterId":"c0","name":"갤럭시","description":"설명","optionAxes":["용량"],
               "variants":[{"listingId":"L0","optionValues":["256GB"]}]}
            ]}
            """;
        VariantGroupingLlmClient client = clientReturning(json);

        Map<String, VariantGroupingLlmClient.LlmGroup> result = client.request(input);

        assertThat(result).containsOnlyKeys("c0");
        assertThat(result.get("c0").name()).isEqualTo("갤럭시");
        assertThat(result.get("c0").variants()).hasSize(1);
    }

    @Test
    @DisplayName("응답에 코드블록/설명이 섞여도 첫 '{'~마지막 '}'만 JSON으로 파싱")
    void extractsJsonFromNoisyContent() throws Exception {
        String noisy = "```json\n{\"groups\":[{\"clusterId\":\"c1\"}]}\n``` 끝";
        VariantGroupingLlmClient client = clientReturning(noisy);

        assertThat(client.request(input)).containsOnlyKeys("c1");
    }

    @Test
    @DisplayName("빈/공백 응답 → 빈 맵 (호출자 전체 fallback 유도)")
    void emptyContentReturnsEmptyMap() throws Exception {
        VariantGroupingLlmClient client = clientReturning("   ");
        assertThat(client.request(input)).isEmpty();
    }

    @Test
    @DisplayName("variant-grouping 프롬프트 누락 → 예외")
    void throwsWhenPromptMissing() {
        VariantGroupingLlmClient client = new VariantGroupingLlmClient(
            mock(ChatClient.class), new DummyPromptProperties(null), objectMapper);
        assertThatThrownBy(() -> client.request(input))
            .isInstanceOf(IllegalStateException.class);
    }

    private VariantGroupingLlmClient clientReturning(String content) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn(content);
        return new VariantGroupingLlmClient(chatClient, prompts, objectMapper);
    }
}
