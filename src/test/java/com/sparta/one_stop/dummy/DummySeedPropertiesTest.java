package com.sparta.one_stop.dummy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DummySeedProperties - 기본값 보정")
class DummySeedPropertiesTest {

    @Test
    @DisplayName("defaultImageUrl이 null이면 CloudFront 기본 이미지로 보정된다 (#520)")
    void nullDefaultImageUrl_fallsBackToCloudFront() {
        DummySeedProperties props = new DummySeedProperties(100, 4000L, null);

        assertThat(props.defaultImageUrl()).isEqualTo(DummySeedProperties.DEFAULT_IMAGE_URL);
        assertThat(props.defaultImageUrl()).startsWith("https://");
    }

    @Test
    @DisplayName("defaultImageUrl이 공백이면 CloudFront 기본 이미지로 보정된다 (#520)")
    void blankDefaultImageUrl_fallsBackToCloudFront() {
        DummySeedProperties props = new DummySeedProperties(100, 4000L, "   ");

        assertThat(props.defaultImageUrl()).isEqualTo(DummySeedProperties.DEFAULT_IMAGE_URL);
    }

    @Test
    @DisplayName("defaultImageUrl이 주어지면 그대로 사용한다")
    void providedDefaultImageUrl_isKept() {
        DummySeedProperties props = new DummySeedProperties(100, 4000L, "https://cdn.example.com/x.png");

        assertThat(props.defaultImageUrl()).isEqualTo("https://cdn.example.com/x.png");
    }
}
