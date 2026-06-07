package com.sparta.one_stop.global.config;

import com.sparta.one_stop.global.storage.ImageStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

// 로컬에 저장된 이미지를 정적 경로로 서빙한다
// app.image.url-prefix(예: /images) 요청을 app.image.local-dir 디렉토리에 매핑
// AWS 도입 후 S3ImageStorage로 교체되면 이 리소스 핸들러는 제거한다
@Configuration
@EnableConfigurationProperties(ImageStorageProperties.class)
@RequiredArgsConstructor
public class ImageStorageConfig implements WebMvcConfigurer {

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
