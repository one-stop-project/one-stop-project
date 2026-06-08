package com.sparta.one_stop.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 로컬 이미지 저장 설정 (app.image.*)
// local-dir  = 실제 파일이 저장되는 디렉토리
// url-prefix = 저장 파일에 접근하는 URL prefix (ImageStorageConfig 리소스 핸들러와 짝을 이룬다)
@ConfigurationProperties(prefix = "app.image")
public record ImageStorageProperties(
        String localDir,
        String urlPrefix
) {
    public ImageStorageProperties {
        // 설정 누락 시 안전한 기본값으로 동작 (앱 기동 실패 방지)
        if (localDir == null || localDir.isBlank()) {
            localDir = "./uploads/images";
        }
        if (urlPrefix == null || urlPrefix.isBlank()) {
            urlPrefix = "/images";
        }
    }
}
