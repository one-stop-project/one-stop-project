package com.sparta.one_stop.dummy.grouping;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.dummy.description.DummyPromptProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 변형 그룹핑의 LLM 호출 책임만 분리 — 사전 군집한 cluster 입력(JSON 직렬화 가능한 Map)을 받아
// 옵션축/옵션값/이름/설명을 LLM에 묻고 clusterId별 그룹으로 파싱한다.
// 호출/파싱 실패는 예외 또는 빈 맵으로 전달 → 호출자(NaverVariantGrouper)가 단일 상품 fallback 처리.
@Component
public class VariantGroupingLlmClient {

    private final ChatClient chatClient;
    private final DummyPromptProperties prompts;
    private final ObjectMapper objectMapper;

    public VariantGroupingLlmClient(@Qualifier("dummyChatClient") ChatClient chatClient,
                                    DummyPromptProperties prompts,
                                    ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.prompts = prompts;
        this.objectMapper = objectMapper;
    }

    // cluster 입력 → clusterId별 LLM 그룹. 응답이 비면 빈 맵(전체 fallback 유도)
    Map<String, LlmGroup> request(Map<String, Object> input) throws Exception {
        if (prompts.variantGrouping() == null) {
            throw new IllegalStateException("dummy.prompts.variant-grouping 설정이 누락되었습니다.");
        }
        String payload = objectMapper.writeValueAsString(input);
        String content = chatClient.prompt()
                .system(prompts.variantGrouping())
                .user(payload)
                .call()
                .content();
        if (content == null || content.isBlank()) {
            return Map.of();
        }
        LlmResponse resp = objectMapper.readValue(extractJson(content), LlmResponse.class);
        Map<String, LlmGroup> byCluster = new HashMap<>();
        if (resp != null && resp.groups() != null) {
            for (LlmGroup g : resp.groups()) {
                if (g.clusterId() != null) {
                    byCluster.put(g.clusterId(), g);
                }
            }
        }
        return byCluster;
    }

    // LLM이 코드블록·설명을 덧붙여도 첫 '{' ~ 마지막 '}' 구간만 JSON으로 취함
    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        return (start >= 0 && end > start) ? content.substring(start, end + 1) : content;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LlmResponse(List<LlmGroup> groups) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LlmGroup(String clusterId, String name, String description,
                    List<String> optionAxes, List<LlmVariant> variants) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LlmVariant(String listingId, List<String> optionValues) {
    }
}
