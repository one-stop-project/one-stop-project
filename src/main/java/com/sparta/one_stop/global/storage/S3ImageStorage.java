package com.sparta.one_stop.global.storage;

import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.util.UUID;

@Slf4j
@Component
@Profile("!local")
@RequiredArgsConstructor
public class S3ImageStorage implements ImageStorage {

    private final S3Client s3Client;
    private final ImageStorageProperties properties;

    @Override
    public String store(byte[] content, String contentType) {
        String extension = ImageStorage.resolveExtension(contentType);
        String key = UUID.randomUUID() + "." + extension;

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) content.length)
                            .build(),
                    RequestBody.fromBytes(content)
            );
        } catch (SdkException e) {
            log.error("S3 업로드 실패: key={}, contentType={}", key, contentType, e);
            throw new CustomException(ErrorCode.COMMON_007);
        }

        return "https://" + properties.bucket() + ".s3." + properties.region() + ".amazonaws.com/" + key;
    }

    @Override
    public void delete(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String key = URI.create(url).getPath().replaceFirst("^/", "");
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
        } catch (Exception e) {
            log.warn("S3 이미지 삭제 실패: {}", url, e);
        }
    }
}
