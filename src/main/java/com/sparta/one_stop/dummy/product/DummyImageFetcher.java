package com.sparta.one_stop.dummy.product;

import com.sparta.one_stop.dummy.DummySeedProperties;
import com.sparta.one_stop.global.storage.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;

// 네이버 이미지 URL을 다운로드해 ImageStorage에 저장 (외부 URL 직접 참조 X, 정책 §9).
// 누락·다운로드 실패·비신뢰 URL 시 기본 이미지 URL 반환.
@Component
@RequiredArgsConstructor
public class DummyImageFetcher {

    private static final Logger log = LoggerFactory.getLogger(DummyImageFetcher.class);
    // 신뢰 호스트(네이버 이미지 CDN)만 다운로드 — 임의 URL 호출(SSRF) 방지
    private static final List<String> TRUSTED_HOST_SUFFIXES = List.of("pstatic.net", "naver.net", "naver.com");

    private final ImageStorage imageStorage;
    private final DummySeedProperties properties;
    private final RestClient imageRestClient = buildClient();

    public String fetchOrDefault(String sourceUrl) {
        URI uri = safeHttpsUri(sourceUrl);
        if (uri == null) {
            return properties.defaultImageUrl();
        }
        try {
            ResponseEntity<byte[]> response = imageRestClient.get()
                    .uri(uri)
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                return properties.defaultImageUrl();
            }
            return imageStorage.store(body, contentType(response.getHeaders().getContentType()));
        } catch (Exception e) {
            log.warn("더미 이미지 다운로드 실패 → 기본 이미지 (url={})", sourceUrl, e);
            return properties.defaultImageUrl();
        }
    }

    // https + 신뢰 호스트만 통과 (SSRF·잘못된 URL 차단). 아니면 null → 기본 이미지로
    private URI safeHttpsUri(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(sourceUrl.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) {
                return null;
            }
            boolean trusted = TRUSTED_HOST_SUFFIXES.stream().anyMatch(host::endsWith);
            if (!trusted) {
                log.warn("비신뢰 이미지 호스트 차단: {}", host);
                return null;
            }
            return uri;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // 신규 생성 실패 시 방금 저장한 이미지 정리 (기본 이미지는 보존)
    public void deleteStored(String url) {
        if (url != null && !url.equals(properties.defaultImageUrl())) {
            imageStorage.delete(url);
        }
    }

    // contentType 헤더가 없으면 jpg로 간주 (네이버 썸네일 대부분 jpg)
    private String contentType(MediaType mediaType) {
        if (mediaType == null) {
            return "image/jpeg";
        }
        return mediaType.getType() + "/" + mediaType.getSubtype();
    }

    // 응답 지연으로 스케줄러 스레드가 무한 블록되지 않게 타임아웃 설정
    private static RestClient buildClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }
}
