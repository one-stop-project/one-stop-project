package com.sparta.one_stop.dummy.description;

import com.sparta.one_stop.dummy.naver.dto.NaverShopItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ProductDescriptionGenerator")
class ProductDescriptionGeneratorTest {

    private final NaverShopItem item = new NaverShopItem(
        "삼성전자 갤럭시 S24 자급제 256GB", "link", "image", "948000", "0",
        "삼성전자", "삼성전자", "디지털", "휴대폰", "자급제", "안드로이드", "pid-1", "1", "네이버");

    private final DummyPromptProperties prompts = new DummyPromptProperties(
        "너는 쇼핑몰 상품 설명 작성기다. 주어진 사실만 사용해 설명을 쓴다.");

    @Test
    @DisplayName("LLM 응답이 있으면 그 내용을 trim 해서 반환")
    void returnsLlmContent() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
            .thenReturn("  삼성전자 갤럭시 S24 자급제 256GB입니다.  ");

        ProductDescriptionGenerator generator = new ProductDescriptionGenerator(chatClient, prompts);
        String desc = generator.generate(item, "스마트폰");

        assertThat(desc).isEqualTo("삼성전자 갤럭시 S24 자급제 256GB입니다.");
    }

    @Test
    @DisplayName("LLM 실패 시 템플릿 fallback (사실 필드로 채움, 빈 값 아님)")
    void fallsBackOnLlmFailure() {
        ChatClient chatClient = mock(ChatClient.class);
        when(chatClient.prompt()).thenThrow(new RuntimeException("LLM 다운"));

        ProductDescriptionGenerator generator = new ProductDescriptionGenerator(chatClient, prompts);
        String desc = generator.generate(item, "스마트폰");

        assertThat(desc).isNotBlank();
        assertThat(desc).contains("삼성전자 갤럭시 S24 자급제 256GB");
        assertThat(desc).contains("스마트폰");
        assertThat(desc).contains("최저가 948,000원");
    }

    @Test
    @DisplayName("LLM 빈 응답이면 fallback")
    void fallsBackOnBlankResponse() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
            .thenReturn("   ");

        ProductDescriptionGenerator generator = new ProductDescriptionGenerator(chatClient, prompts);
        String desc = generator.generate(item, "스마트폰");

        assertThat(desc).contains("삼성전자 갤럭시 S24 자급제 256GB");
    }
}
