package com.sparta.one_stop.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 이미지 저장 설정 (app.image.*) — 로컬/S3 두 프로필이 한 레코드를 공유한다
// 로컬(local 프로필)  : local-dir(저장 디렉토리) · url-prefix(접근 URL prefix, 리소스 핸들러와 짝)
// S3(!local 프로필)   : bucket · region · cloudfront-domain(설정 시 CloudFront URL 반환)
// 쓰지 않는 쪽 설정이 비어도 기동을 막지 않는다 — bucket 필수 검증은 S3 빈 생성 시점(ImageStorageConfig)에서 수행
@ConfigurationProperties(prefix = "app.image")
public record ImageStorageProperties(
        String localDir,
        String urlPrefix,
        String bucket,
        String region,
        String cloudfrontDomain
) {
    public ImageStorageProperties {
        // 로컬 설정 누락 시 안전한 기본값으로 동작 (앱 기동 실패 방지)
        if (localDir == null || localDir.isBlank()) {
            localDir = "./uploads/images";
        }
        if (urlPrefix == null || urlPrefix.isBlank()) {
            urlPrefix = "/images";
        }
        if (region == null || region.isBlank()) {
            region = "ap-northeast-2";
        }
    }
}
