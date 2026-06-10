package com.sparta.one_stop.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.image")
public record ImageStorageProperties(
        String bucket,
        String region
) {
    public ImageStorageProperties {
        if (region == null || region.isBlank()) {
            region = "ap-northeast-2";
        }
    }
}
