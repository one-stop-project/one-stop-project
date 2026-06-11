package com.sparta.one_stop.global.config;

import com.sparta.one_stop.global.storage.ImageStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;

// 이미지 저장 인프라를 프로필별로 분리한다
// local        → 로컬 파일시스템 + 정적 리소스 핸들러 (LocalImageStorage와 짝)
// !local(배포) → S3Client 빈 (S3ImageStorage와 짝)
@Configuration
@EnableConfigurationProperties(ImageStorageProperties.class)
public class ImageStorageConfig {

    // 로컬에 저장된 이미지를 url-prefix(예: /images) 요청으로 정적 서빙
    @Configuration
    @Profile("local")
    @RequiredArgsConstructor
    static class LocalResourceConfig implements WebMvcConfigurer {

        private final ImageStorageProperties properties;

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            // 리소스 위치는 디렉토리이므로 끝이 "/"로 닫혀야 Spring이 하위 파일을 찾는다
            String location = Path.of(properties.localDir()).toAbsolutePath().toUri().toString();
            if (!location.endsWith("/")) {
                location += "/";
            }
            registry.addResourceHandler(properties.urlPrefix() + "/**")
                    .addResourceLocations(location);
        }
    }

    // 배포 환경 S3 클라이언트 — bucket 미설정 시 기동 시점에 즉시 실패시켜 잘못된 배포를 막는다
    @Configuration
    @Profile("!local")
    static class S3Config {

        @Bean
        public S3Client s3Client(ImageStorageProperties properties) {
            if (properties.bucket() == null || properties.bucket().isBlank()) {
                throw new IllegalStateException("app.image.bucket must not be blank for S3 image storage");
            }
            return S3Client.builder()
                    .region(Region.of(properties.region()))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        }
    }
}
