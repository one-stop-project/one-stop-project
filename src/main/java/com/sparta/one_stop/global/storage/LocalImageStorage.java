package com.sparta.one_stop.global.storage;

import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

// 로컬 파일시스템 기반 이미지 저장 (local 프로필 — 배포는 S3ImageStorage가 담당)
// 저장 위치 = app.image.local-dir, 접근 URL = app.image.url-prefix + "/{uuid}.{ext}"
// 삭제는 @Async(eventExecutor)로 fire-and-forget — 응답 지연 없이 파일을 정리한다
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalImageStorage implements ImageStorage {

    private final ImageStorageProperties properties;

    @Override
    public String store(byte[] content, String contentType) {
        String extension = ImageStorage.resolveExtension(contentType);
        // 파일명을 UUID로 생성해 충돌과 경로 조작(path traversal) 입력 원천 차단
        String filename = UUID.randomUUID() + "." + extension;
        Path directory = Path.of(properties.localDir());
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(filename), content);
        } catch (IOException e) {
            log.error("이미지 로컬 저장 실패: dir={}, file={}", directory, filename, e);
            throw new CustomException(ErrorCode.COMMON_007);
        }
        return properties.urlPrefix() + "/" + filename;
    }

    @Async("eventExecutor")
    @Override
    public void delete(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        // 저장 URL의 마지막 세그먼트가 파일명. 외부 URL 등 디렉토리에 없는 값이면 deleteIfExists가 무시
        String filename = url.substring(url.lastIndexOf('/') + 1);
        Path dir = Path.of(properties.localDir()).toAbsolutePath().normalize();
        Path target = dir.resolve(filename).normalize();
        // 정규화 후 저장 디렉토리 밖을 가리키면(경로 조작 ".." 등) 삭제하지 않는다
        if (!target.startsWith(dir)) {
            log.warn("이미지 삭제 경로가 저장 디렉토리를 벗어남: {}", target);
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // 삭제 실패는 치명적이지 않으므로 로그만 남김
            log.warn("이미지 로컬 삭제 실패: {}", target, e);
        }
    }
}
