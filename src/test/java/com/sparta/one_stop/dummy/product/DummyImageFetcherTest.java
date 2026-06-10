package com.sparta.one_stop.dummy.product;

import com.sparta.one_stop.dummy.DummySeedProperties;
import com.sparta.one_stop.global.storage.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DummyImageFetcher - 신뢰 호스트 검증 (SSRF 차단)")
class DummyImageFetcherTest {

    private static final String DEFAULT = "/images/default-product.png";

    private final ImageStorage imageStorage = mock(ImageStorage.class);
    private final DummySeedProperties properties = mock(DummySeedProperties.class);
    private final DummyImageFetcher fetcher = new DummyImageFetcher(imageStorage, properties);

    @BeforeEach
    void setUp() {
        when(properties.defaultImageUrl()).thenReturn(DEFAULT);
    }

    @Test
    @DisplayName("신뢰 도메인 흉내 낸 가짜 호스트(attacker-naver.com)는 차단 → 기본 이미지, 다운로드 안 함")
    void blocksLookalikeHost() {
        String result = fetcher.fetchOrDefault("https://attacker-naver.com/x.jpg");

        assertThat(result).isEqualTo(DEFAULT);
        verify(imageStorage, never()).store(any(), any());
    }

    @Test
    @DisplayName("신뢰 서브도메인이 아닌 임의 호스트는 차단")
    void blocksUntrustedHost() {
        assertThat(fetcher.fetchOrDefault("https://evil.com/x.jpg")).isEqualTo(DEFAULT);
        verify(imageStorage, never()).store(any(), any());
    }

    @Test
    @DisplayName("https가 아니면 차단")
    void blocksNonHttps() {
        assertThat(fetcher.fetchOrDefault("http://shopping.naver.com/x.jpg")).isEqualTo(DEFAULT);
        verify(imageStorage, never()).store(any(), any());
    }

    @Test
    @DisplayName("null/빈 URL은 기본 이미지")
    void blocksNullOrBlank() {
        assertThat(fetcher.fetchOrDefault(null)).isEqualTo(DEFAULT);
        assertThat(fetcher.fetchOrDefault("  ")).isEqualTo(DEFAULT);
        verify(imageStorage, never()).store(any(), any());
    }
}
