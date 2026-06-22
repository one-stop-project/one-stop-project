package com.sparta.one_stop.global.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sparta.one_stop.global.ai.prompt.AiPromptProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
@Configuration
@EnableConfigurationProperties(AiPromptProperties.class)
public class AiConfig {

    // Spring AI가 Gemini 응답의 thought_signature를 내부 ToolCall 모델로 역직렬화할 때 손실시킴.
    // 응답을 버퍼링해 thought_signature를 ThreadLocal에 저장 → 2차 요청의 tool_calls에 재주입.
    private static final ThreadLocal<Map<String, String>> THOUGHT_SIGNATURES = new ThreadLocal<>();


    private static boolean hasAssistantToolCalls(JsonNode root){
        for( JsonNode message : root.path("messages")){
            JsonNode tc = message.path("tool_calls");
            if(tc.isArray() && !tc.isEmpty()) return true;
        }
        return false;
    }

    private static Map<String, String> extractThoughtSignatures(ObjectMapper mapper, byte[] responseBytes) {
        Map<String, String> signatures = new HashMap<>();
        try {
            JsonNode root = mapper.readTree(responseBytes);
            JsonNode choices = root.path("choices");
            if (!choices.isArray()) return signatures;
            for (JsonNode choice : choices) {
                JsonNode toolCalls = choice.path("message").path("tool_calls");
                if (!toolCalls.isArray()) continue;
                for (JsonNode tc : toolCalls) {
                    String id = tc.path("id").asText();
                    JsonNode sig = tc.path("extra_content").path("google").path("thought_signature");
                    if (!id.isEmpty() && !sig.isMissingNode() && !sig.isNull()) {
                        signatures.put(id, sig.asText());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Gemini] thought_signature extraction failed", e);
        }
        return signatures;
    }

    private static void injectThoughtSignatures(JsonNode root, Map<String, String> signatures) {
        JsonNode messages = root.path("messages");
        if (!messages.isArray()) return;
        for (JsonNode msg : messages) {
            JsonNode toolCalls = msg.path("tool_calls");
            if (!toolCalls.isArray()) continue;
            for (JsonNode tc : toolCalls) {
                if(!tc.isObject()) continue;
                String id = tc.path("id").asText();
                String sig = signatures.get(id);

                if(sig == null) continue;

                ObjectNode tcObj = (ObjectNode) tc;
                ObjectNode extra = tcObj.get("extra_content") instanceof ObjectNode e? e: tcObj.putObject("extra_content");
                ObjectNode google = extra.get("google") instanceof ObjectNode g? g: extra.putObject("google");
                google.put("thought_signature", sig);
//
//                if (tc.path("function").isObject()) {
//                    ((ObjectNode) tc.path("function")).put("thought_signature", sig);
//                }
            }
        }
    }

    @Bean
    @Primary
    public ChatClient mainChatClient(
        @Value("${spring.ai.openai.api-key}") String apiKey,
        @Value("${spring.ai.openai.base-url}") String baseUrl,
        @Value("${spring.ai.openai.chat.completions-path:/chat/completions}") String completionsPath,
        @Value("${spring.ai.openai.chat.options.model:gemini-3.1-flash-lite}") String model,
        ObjectMapper objectMapper
    ) {
        RestClient.Builder restClientBuilder = RestClient.builder()
            .requestInterceptor((request, body, execution) -> {
                byte[] modifiedBody = body;
                if (body.length > 0) {
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        removeFieldExceptToolCalls(root, "thought_signature");

                        // 새 대화 첫 요청에서 초기화
                        if (!hasAssistantToolCalls(root)){
                            THOUGHT_SIGNATURES.remove();
                        }

                        Map<String, String> sigs = THOUGHT_SIGNATURES.get();

                        if (sigs != null && !sigs.isEmpty()) {
                            injectThoughtSignatures(root, sigs);
                        }

                        modifiedBody = objectMapper.writeValueAsBytes(root);
                    } catch (Exception e) {
                        log.warn("[Gemini] request modification failed", e);
                        THOUGHT_SIGNATURES.remove();
                    }
                }

                ClientHttpResponse response = execution.execute(request, modifiedBody);

                try {
                    byte[] responseBytes = response.getBody().readAllBytes();
                    Map<String, String> extracted = extractThoughtSignatures(objectMapper, responseBytes);
                    if (!extracted.isEmpty()) {
                        Map<String, String> acc = THOUGHT_SIGNATURES.get();
                        if(acc == null) {
                            acc = new HashMap<>();
                            THOUGHT_SIGNATURES.set(acc);
                        }
//                        THOUGHT_SIGNATURES.set(extracted);
                        acc.putAll(extracted);
                    }
                    return new BufferedClientHttpResponse(response, responseBytes);
                } catch (Exception e) {
                    log.warn("[Gemini] response buffering failed", e);
                    return new BufferedClientHttpResponse(response, new byte[0]);
                }
            });

        OpenAiApi api = OpenAiApi.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .completionsPath(completionsPath)
            .restClientBuilder(restClientBuilder)
            .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(OpenAiChatOptions.builder().model(model).build())
            .build();

        return ChatClient.builder(chatModel).build();
    }

    private void removeFieldExceptToolCalls(JsonNode node, String fieldName) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            obj.remove(fieldName);
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (!entry.getKey().equals("tool_calls")) {
                    removeFieldExceptToolCalls(entry.getValue(), fieldName);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                removeFieldExceptToolCalls(child, fieldName);
            }
        }
    }

    // 더미 AI (junghyun 키, S2는 GEMINI_API_KEY fallback) — 상품 더미데이터 생성 전용
    @Bean
    public ChatClient dummyChatClient(
        @Value("${gemini.dummy-api-key}") String dummyApiKey,
        @Value("${spring.ai.openai.base-url}") String baseUrl,
        @Value("${spring.ai.openai.chat.completions-path:/chat/completions}") String completionsPath,
        @Value("${spring.ai.openai.chat.options.model:gemini-2.5-flash-lite}") String model
    ) {
        OpenAiApi api = OpenAiApi.builder()
            .apiKey(dummyApiKey)
            .baseUrl(baseUrl)
            .completionsPath(completionsPath)
            .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(OpenAiChatOptions.builder().model(model).build())
            .build();
        return ChatClient.builder(chatModel).build();
    }

    private static class BufferedClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final byte[] bufferedBody;

        BufferedClientHttpResponse(ClientHttpResponse delegate, byte[] bufferedBody) {
            this.delegate = delegate;
            this.bufferedBody = bufferedBody;
        }

        @Override public InputStream getBody() { return new ByteArrayInputStream(bufferedBody); }
        @Override public HttpStatusCode getStatusCode() throws IOException { return delegate.getStatusCode(); }
        @Override public String getStatusText() throws IOException { return delegate.getStatusText(); }
        @Override public HttpHeaders getHeaders() { return delegate.getHeaders(); }
        @Override public void close() { delegate.close(); }
    }


}
