package com.sparta.one_stop.dummy.grouping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.dummy.description.DummyPromptProperties;
import com.sparta.one_stop.dummy.naver.dto.NaverShopItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("NaverVariantGrouper")
class NaverVariantGrouperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DummyPromptProperties prompts = new DummyPromptProperties(null, "변형을 묶어 옵션을 만들어라");

    // 같은 모델의 변형 2개(256GB/128GB)는 규칙으로 한 cluster(c0), 다른 모델(아이폰)은 c1
    private final NaverShopItem galaxy256 = item("삼성전자 갤럭시 S24 256GB", "삼성전자", "1100000", "g256", "img-g");
    private final NaverShopItem galaxy128 = item("삼성전자 갤럭시 S24 128GB", "삼성전자", "900000", "g128", "img-g2");
    private final NaverShopItem iphone = item("애플 아이폰 17", "Apple", "1500000", "i17", "img-i");

    @Test
    @DisplayName("LLM 그룹핑 성공 → 변형을 하나의 상품 + 옵션으로 묶는다")
    void groupsVariantsWithOptions() {
        String json = """
            {"groups":[
              {"clusterId":"c0","name":"갤럭시 S24","description":"삼성 플래그십 스마트폰","optionAxes":["용량"],
               "variants":[{"listingId":"L0","optionValues":["256GB"]},{"listingId":"L1","optionValues":["128GB"]}]},
              {"clusterId":"c1","name":"아이폰 17","description":"애플 스마트폰","optionAxes":[],
               "variants":[{"listingId":"L2","optionValues":[]}]}
            ]}
            """;
        NaverVariantGrouper grouper = grouperReturning(json);

        List<GroupedProduct> result = grouper.group(
            List.of(galaxy256, galaxy128, iphone), List.of(1L, 2L, 3L), "스마트폰");

        assertThat(result).hasSize(2);

        GroupedProduct galaxy = result.get(0);
        assertThat(galaxy.name()).isEqualTo("갤럭시 S24");
        assertThat(galaxy.categoryIds()).containsExactly(1L, 2L, 3L);
        assertThat(galaxy.optionAxisNames()).containsExactly("용량");
        assertThat(galaxy.variants()).hasSize(2);
        assertThat(galaxy.variants()).extracting(ProductVariant::sourcePrice)
            .containsExactly(1100000L, 900000L);
        assertThat(galaxy.variants().get(0).optionValues()).containsExactly("256GB");

        GroupedProduct ip = result.get(1);
        assertThat(ip.optionAxisNames()).isEmpty();
        assertThat(ip.variants()).hasSize(1);
        assertThat(ip.variants().get(0).sourcePrice()).isEqualTo(1500000L);
    }

    @Test
    @DisplayName("LLM 실패 → 각 listing을 옵션 없는 단일 상품으로 fallback (false-split)")
    void fallsBackToSingles() {
        ChatClient chatClient = mock(ChatClient.class);
        when(chatClient.prompt()).thenThrow(new RuntimeException("LLM 다운"));
        NaverVariantGrouper grouper = new NaverVariantGrouper(chatClient, prompts, objectMapper);

        List<GroupedProduct> result = grouper.group(
            List.of(galaxy256, galaxy128, iphone), List.of(1L), "스마트폰");

        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(g -> {
            assertThat(g.optionAxisNames()).isEmpty();
            assertThat(g.variants()).hasSize(1);
        });
    }

    @Test
    @DisplayName("입력이 비면 빈 결과")
    void emptyInput() {
        NaverVariantGrouper grouper = grouperReturning("{\"groups\":[]}");
        assertThat(grouper.group(List.of(), List.of(1L), "스마트폰")).isEmpty();
    }

    private NaverVariantGrouper grouperReturning(String json) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn(json);
        return new NaverVariantGrouper(chatClient, prompts, objectMapper);
    }

    private NaverShopItem item(String title, String brand, String lprice, String productId, String image) {
        return new NaverShopItem(title, "link", image, lprice, "0", brand, brand,
            "디지털", "휴대폰", "스마트폰", "", productId, "1", "네이버");
    }
}
