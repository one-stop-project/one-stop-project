package com.sparta.one_stop.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalImageStorageTest {

    // 임시 디렉토리에 실제로 저장/삭제하는 어댑터 (테스트마다 @TempDir 주입)
    // bucket/region은 local 프로필에서 쓰지 않으므로 null (기본값 보정됨)
    private LocalImageStorage storageOn(Path dir) {
        return new LocalImageStorage(new ImageStorageProperties(dir.toString(), "/images", null, null, null));
    }

    @Test
    void store_jpeg_returnsUrlWithJpgExtensionAndWritesFile(@TempDir Path dir) throws IOException {
        String url = storageOn(dir).store("hello".getBytes(), "image/jpeg");

        // 반환 URL = url-prefix + /{uuid}.jpg
        assertThat(url).startsWith("/images/").endsWith(".jpg");
        // 실제 파일이 디렉토리에 기록됨
        String filename = url.substring(url.lastIndexOf('/') + 1);
        assertThat(Files.readAllBytes(dir.resolve(filename))).isEqualTo("hello".getBytes());
    }

    @Test
    void store_png_returnsPngExtension(@TempDir Path dir) {
        assertThat(storageOn(dir).store(new byte[]{1, 2, 3}, "image/png")).endsWith(".png");
    }

    @Test
    void store_contentTypeWithParameters_resolvesExtension(@TempDir Path dir) {
        // "image/jpeg; charset=utf-8"처럼 파라미터가 붙어도 확장자를 해석한다
        assertThat(storageOn(dir).store(new byte[]{1}, "image/jpeg; charset=utf-8")).endsWith(".jpg");
    }

    @Test
    void store_unsupportedContentType_throwsCommon006(@TempDir Path dir) {
        assertThatThrownBy(() -> storageOn(dir).store(new byte[]{1}, "application/pdf"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMMON_006);
    }

    @Test
    void delete_removesStoredFile(@TempDir Path dir) {
        LocalImageStorage storage = storageOn(dir);
        String url = storage.store("data".getBytes(), "image/jpeg");
        String filename = url.substring(url.lastIndexOf('/') + 1);
        assertThat(Files.exists(dir.resolve(filename))).isTrue();

        storage.delete(url);

        assertThat(Files.exists(dir.resolve(filename))).isFalse();
    }

    @Test
    void delete_externalOrUnknownUrl_isNoOpWithoutError(@TempDir Path dir) {
        // 외부 URL 등 저장소에 없는 값은 예외 없이 무시 (예외만 안 나면 통과)
        storageOn(dir).delete("https://example.com/foo.jpg");
    }
}
