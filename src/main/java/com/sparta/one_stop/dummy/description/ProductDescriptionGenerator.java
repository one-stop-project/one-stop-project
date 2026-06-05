package com.sparta.one_stop.dummy.description;

import com.sparta.one_stop.dummy.naver.dto.NaverShopItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// 네이버 필드로 상품 설명을 LLM 그라운딩 생성 (없는 사실 창작 금지).
// LLM 실패·빈 응답 시 필드 템플릿으로 Fallback → description(NOT NULL·FULLTEXT) 항상 채움
@Component
public class ProductDescriptionGenerator {

    private static final Logger log = LoggerFactory.getLogger(ProductDescriptionGenerator.class);

    private final ChatClient chatClient;
    private final DummyPromptProperties prompts;

    public ProductDescriptionGenerator(ChatClient chatClient, DummyPromptProperties prompts) {
        this.chatClient = chatClient;
        this.prompts = prompts;
    }

    public String generate(NaverShopItem item, String categoryName) {
        if (prompts.productDescription() == null) {
            throw new IllegalStateException("dummy.prompts.product-description 설정이 누락되었습니다.");
        }
        try {
            String content = chatClient.prompt()
                .system(prompts.productDescription())
                .user(buildFacts(item, categoryName))
                .call()
                .content();
            if (content == null || content.isBlank()) {
                return fallback(item, categoryName);
            }
            return content.trim();
        } catch (Exception e) {
            log.warn("LLM 설명 생성 실패 → 템플릿 fallback (productId={})", item.productId(), e);
            return fallback(item, categoryName);
        }
    }

    // LLM에 넘기는 사실 목록 (빈 필드는 생략)
    private String buildFacts(NaverShopItem item, String categoryName) {
        StringBuilder sb = new StringBuilder("상품 정보:\n");
        appendFact(sb, "상품명", item.title());
        appendFact(sb, "브랜드", item.brand());
        appendFact(sb, "제조사", item.maker());
        appendFact(sb, "카테고리", categoryName);
        appendFact(sb, "최저가", formatPrice(item.lprice()));
        return sb.toString();
    }

    // LLM 실패 시 결정적 템플릿 — 사실 필드를 " · "로 연결
    private String fallback(NaverShopItem item, String categoryName) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, item.title());
        addIfPresent(parts, categoryName);
        String price = formatPrice(item.lprice());
        if (price != null) {
            parts.add("최저가 " + price);
        }
        return parts.isEmpty() ? "상품 설명 준비중입니다." : String.join(" · ", parts);
    }

    private void appendFact(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("- ").append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    private void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }

    private String formatPrice(String lprice) {
        if (lprice == null || lprice.isBlank()) {
            return null;
        }
        try {
            return String.format("%,d원", Long.parseLong(lprice.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
