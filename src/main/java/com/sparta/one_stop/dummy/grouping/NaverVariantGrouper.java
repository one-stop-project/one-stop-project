package com.sparta.one_stop.dummy.grouping;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.dummy.description.DummyPromptProperties;
import com.sparta.one_stop.dummy.naver.dto.NaverShopItem;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// 네이버 검색 결과(한 카테고리분)를 "기본 상품 + 옵션 변형"으로 묶는다.
// 그룹 경계·식별키는 규칙(결정적)으로 정해 재실행 멱등을 보장하고,
// 묶인 cluster 안에서 옵션축/옵션값/이름/설명만 LLM이 채운다(LLM 실패 시 단일 상품으로 분리 fallback).
@Component
@RequiredArgsConstructor
public class NaverVariantGrouper {

    private static final Logger log = LoggerFactory.getLogger(NaverVariantGrouper.class);
    private static final String SOURCE_PREFIX = "NAVER|";
    private static final int MAX_AXES = 5;

    // 변형 토큰(용량) — 숫자+단위. 모델/버전 토큰은 보존(false-merge 방지)
    private static final Pattern CAPACITY =
            Pattern.compile("\\d+\\s?(tb|gb|mb|kg|ml|l|g|매|개|입|포|정|구)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRACKET = Pattern.compile("\\[[^\\]]*\\]");
    // 변형 토큰(색상) — 사전 기반(단어 단위 제거). 모델명과 겹칠 위험 낮은 명확한 색상만.
    private static final Set<String> COLORS = Set.of(
            "블랙", "화이트", "레드", "블루", "그린", "핑크", "그레이", "그래파이트", "실버", "골드", "로즈골드",
            "네이비", "베이지", "브라운", "퍼플", "바이올렛", "옐로우", "민트", "카키", "아이보리", "크림",
            "라벤더", "스카이", "코랄", "와인", "오렌지", "차콜",
            "black", "white", "red", "blue", "green", "pink", "gray", "grey", "silver", "gold", "navy",
            "beige", "brown", "purple", "yellow", "mint", "khaki", "ivory", "coral", "wine", "orange");

    private final ChatClient chatClient;
    private final DummyPromptProperties prompts;
    private final ObjectMapper objectMapper;

    public List<GroupedProduct> group(List<NaverShopItem> items, List<Long> categoryIds, String categoryName) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        // 1. 규칙 기반 사전 군집 (결정적 baseKey)
        List<Cluster> clusters = preGroup(items);

        // 2. cluster 안의 옵션/이름/설명을 LLM 배치 1콜로 채움 (실패 시 빈 맵 → 전체 fallback)
        Map<String, LlmGroup> llm;
        try {
            llm = callLlm(clusters);
        } catch (Exception e) {
            log.warn("변형 그룹핑 LLM 실패 → 단일 상품 fallback (clusters={})", clusters.size(), e);
            llm = Map.of();
        }

        // 3. cluster → GroupedProduct (가드 + fallback)
        List<GroupedProduct> result = new ArrayList<>();
        for (Cluster cluster : clusters) {
            result.addAll(mapCluster(cluster, llm.get(cluster.clusterId()), categoryIds, categoryName));
        }
        return result;
    }

    // ── 1) 규칙 사전 군집 ──
    private List<Cluster> preGroup(List<NaverShopItem> items) {
        LinkedHashMap<String, Cluster> byBase = new LinkedHashMap<>();
        int listingSeq = 0;
        for (NaverShopItem item : items) {
            String baseKey = baseKey(item);
            Cluster cluster = byBase.computeIfAbsent(baseKey,
                    k -> new Cluster("c" + byBase.size(), k, new ArrayList<>()));
            cluster.members().add(new Member("L" + listingSeq++, item));
        }
        return new ArrayList<>(byBase.values());
    }

    // ── 2) LLM 배치 호출 ──
    private Map<String, LlmGroup> callLlm(List<Cluster> clusters) throws Exception {
        if (prompts.variantGrouping() == null) {
            throw new IllegalStateException("dummy.prompts.variant-grouping 설정이 누락되었습니다.");
        }
        String payload = objectMapper.writeValueAsString(buildInput(clusters));
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

    private Map<String, Object> buildInput(List<Cluster> clusters) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Cluster c : clusters) {
            List<Map<String, Object>> listings = new ArrayList<>();
            for (Member m : c.members()) {
                listings.add(Map.of(
                        "listingId", m.listingId(),
                        "title", cleanedTitle(m.item()),
                        "brand", nullSafe(m.item().brand()),
                        "maker", nullSafe(m.item().maker())));
            }
            out.add(Map.of("clusterId", c.clusterId(), "listings", listings));
        }
        return Map.of("clusters", out);
    }

    // ── 3) cluster → GroupedProduct ──
    private List<GroupedProduct> mapCluster(Cluster cluster, LlmGroup llm,
                                            List<Long> categoryIds, String categoryName) {
        if (llm == null || llm.variants() == null || llm.variants().isEmpty()) {
            return fallbackSingles(cluster, categoryIds, categoryName);
        }
        List<String> axes = capAxes(llm.optionAxes());
        // 다변형인데 옵션축이 비면 LLM이 구분 정보를 못 살린 것 → false-split이 안전
        if (axes.isEmpty() && cluster.members().size() > 1) {
            return fallbackSingles(cluster, categoryIds, categoryName);
        }

        Map<String, List<String>> valuesByListing = new HashMap<>();
        for (LlmVariant v : llm.variants()) {
            if (v.listingId() != null) {
                valuesByListing.put(v.listingId(), v.optionValues());
            }
        }

        List<ProductVariant> variants = new ArrayList<>();
        Set<String> seenCombo = new HashSet<>();
        for (Member m : cluster.members()) {
            List<String> values = fitValues(valuesByListing.get(m.listingId()), axes.size());
            if (!seenCombo.add(String.join("", values))) {
                continue;  // 같은 옵션 조합 중복 변형 제거 (ProductItem unique 제약 보호)
            }
            variants.add(new ProductVariant(listingSourceKey(m.item()), values, price(m.item())));
        }
        if (variants.isEmpty()) {
            return fallbackSingles(cluster, categoryIds, categoryName);
        }

        NaverShopItem rep = cluster.members().get(0).item();
        String name = firstNonBlank(llm.name(), cleanedTitle(rep));
        String description = firstNonBlank(llm.description(), descriptionFallback(rep, categoryName));
        return List.of(new GroupedProduct(
                baseSourceKey(cluster.baseKey()), name, description, categoryIds, axes, rep.image(), variants));
    }

    // LLM 실패/가드 탈락 → 각 listing을 옵션 없는 단일 상품으로 (false-split, 안전)
    private List<GroupedProduct> fallbackSingles(Cluster cluster, List<Long> categoryIds, String categoryName) {
        List<GroupedProduct> out = new ArrayList<>();
        for (Member m : cluster.members()) {
            String lkey = listingSourceKey(m.item());
            out.add(new GroupedProduct(
                    lkey,  // 단일 상품은 그룹=자기 자신 (listing 키로 고유)
                    cleanedTitle(m.item()),
                    descriptionFallback(m.item(), categoryName),
                    categoryIds,
                    List.of(),
                    m.item().image(),
                    List.of(new ProductVariant(lkey, List.of(), price(m.item())))));
        }
        return out;
    }

    // ── 키/정규화 ──
    private String baseKey(NaverShopItem item) {
        return norm(item.brand()) + "|" + norm(item.maker()) + "|" + stripVariantTokens(cleanedTitle(item));
    }

    private String baseSourceKey(String baseKey) {
        return SOURCE_PREFIX + baseKey;
    }

    private String listingSourceKey(NaverShopItem item) {
        if (item.productId() != null && !item.productId().isBlank()) {
            return SOURCE_PREFIX + "pid:" + item.productId().trim();
        }
        return SOURCE_PREFIX + "t:" + norm(item.brand()) + "|" + norm(item.maker()) + "|" + norm(item.title());
    }

    private String stripVariantTokens(String title) {
        String t = CAPACITY.matcher(title.toLowerCase(Locale.ROOT)).replaceAll(" ");
        StringBuilder sb = new StringBuilder();
        for (String token : t.split("\\s+")) {
            if (!token.isBlank() && !COLORS.contains(token)) {
                sb.append(token);
            }
        }
        return sb.toString();
    }

    private String cleanedTitle(NaverShopItem item) {
        String t = item.title() == null ? "" : item.title();
        t = BRACKET.matcher(t).replaceAll(" ");
        return t.replaceAll("\\s+", " ").trim();
    }

    private String norm(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private Long price(NaverShopItem item) {
        try {
            return Long.parseLong(item.lprice().trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private List<String> capAxes(List<String> axes) {
        if (axes == null) {
            return List.of();
        }
        return axes.stream().filter(a -> a != null && !a.isBlank()).limit(MAX_AXES).toList();
    }

    // optionValues를 축 개수에 맞춤 (모자라면 "" 패딩, 넘치면 자름)
    private List<String> fitValues(List<String> values, int size) {
        List<String> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String v = (values != null && i < values.size()) ? values.get(i) : null;
            out.add(v == null ? "" : v.trim());
        }
        return out;
    }

    private String descriptionFallback(NaverShopItem item, String categoryName) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, cleanedTitle(item));
        addIfPresent(parts, categoryName);
        Long p = price(item);
        if (p != null && p > 0) {
            parts.add(String.format("최저가 %,d원", p));
        }
        return parts.isEmpty() ? "상품 설명 준비중입니다." : String.join(" · ", parts);
    }

    private void addIfPresent(List<String> parts, String v) {
        if (v != null && !v.isBlank()) {
            parts.add(v.trim());
        }
    }

    private String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a.trim() : b;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        return (start >= 0 && end > start) ? content.substring(start, end + 1) : content;
    }

    // ── 내부 타입 ──
    private record Cluster(String clusterId, String baseKey, List<Member> members) {
    }

    private record Member(String listingId, NaverShopItem item) {
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
