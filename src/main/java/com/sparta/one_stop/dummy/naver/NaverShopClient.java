package com.sparta.one_stop.dummy.naver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.dummy.naver.dto.NaverShopItem;
import com.sparta.one_stop.dummy.naver.dto.NaverShopResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

// 네이버 쇼핑 검색 API 호출 → 상품 목록 조회
// title의 <b> 태그·HTML 엔티티 정제, 오류 응답은 즉시 예외(fail-fast)로 전환
public class NaverShopClient {

    private static final Logger log = LoggerFactory.getLogger(NaverShopClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;

    public NaverShopClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<NaverShopItem> search(String query, int display, int start, String sort) {
        validate(query, display, start);

        NaverShopResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("query", query)
                        .queryParam("display", display)
                        .queryParam("start", start)
                        .queryParam("sort", sort)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                    String body = new String(httpResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw naverApiException(httpResponse.getStatusCode(), body);
                })
                .body(NaverShopResponse.class);

        if (response == null) {
            log.warn("Naver shop API returned a null response object");
            return Collections.emptyList();
        }
        if (response.items() == null) {
            log.warn("Naver shop API returned a response with null items");
            return Collections.emptyList();
        }
        if (response.items().isEmpty()) {
            log.warn("Naver shop API returned a response with an empty items list");
            return Collections.emptyList();
        }

        return response.items().stream()
                .map(item -> item.withTitle(cleanTitle(item.title())))
                .toList();
    }

    private static void validate(String query, int display, int start) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query must be non-blank");
        }
        if (display < 1 || display > 100) {
            throw new IllegalArgumentException("display must be between 1 and 100");
        }
        if (start < 1 || start > 1000) {
            throw new IllegalArgumentException("start must be between 1 and 1000");
        }
    }

    private static String cleanTitle(String title) {
        if (title == null) {
            return null;
        }
        String withoutBoldTags = title.replaceAll("(?i)</?b>", "");
        return HtmlUtils.htmlUnescape(withoutBoldTags);
    }

    private static IllegalStateException naverApiException(HttpStatusCode statusCode, String body) {
        String errorCode = "";
        String errorMessage = "";

        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            errorCode = root.path("errorCode").asText("");
            errorMessage = root.path("errorMessage").asText("");
        } catch (IOException ignored) {
            errorMessage = body == null ? "" : body;
        }

        return new IllegalStateException(
                "Naver API error " + statusCode.value()
                        + ": errorCode=" + errorCode
                        + " errorMessage=" + errorMessage
        );
    }
}
