package com.sparta.one_stop.dummy.product;

import com.sparta.one_stop.dummy.DummySeedProperties;
import com.sparta.one_stop.global.storage.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;

// 네이버 이미지 URL을 다운로드해 ImageStorage에 저장 (외부 URL 직접 참조 X, 정책 §9).
// 누락·다운로드 실패 시 기본 이미지 URL 반환.
@Component
@RequiredArgsConstructor
public class DummyImageFetcher {

    private static final Logger log = LoggerFactory.getLogger(DummyImageFetcher.class);

    private final ImageStorage imageStorage;
    private final DummySeedProperties properties;
    private final RestClient imageRestClient = RestClient.create();

    public String fetchOrDefault(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return properties.defaultImageUrl();
        }
        try {
            ResponseEntity<byte[]> response = imageRestClient.get()
                    .uri(URI.create(sourceUrl))
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

    // contentType 헤더가 없으면 jpg로 간주 (네이버 썸네일 대부분 jpg)
    private String contentType(MediaType mediaType) {
        if (mediaType == null) {
            return "image/jpeg";
        }
        return mediaType.getType() + "/" + mediaType.getSubtype();
    }
}
