package com.sparta.one_stop.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.image")
public record ImageStorageProperties(
        String bucket,
        String region
) {
    public ImageStorageProperties {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("app.image.bucket must not be blank");
        }
        if (region == null || region.isBlank()) {
            region = "ap-northeast-2";
        }
    }
}
