package com.sparta.one_stop.global.storage;

import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;

import java.util.Locale;
import java.util.Map;

// 이미지 저장 추상화 (포트)
// 구현체 = LocalImageStorage(local 프로필, 로컬 파일시스템) / S3ImageStorage(!local 프로필, S3)
// 입력을 바이트 + contentType으로만 받아 저장 매체에 종속되지 않는다
public interface ImageStorage {

    // 저장 허용 contentType → 파일 확장자 (포트 계약 — 어떤 어댑터든 같은 형식만 받는다)
    Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    // 이미지 바이트를 저장하고 접근 가능한 URL을 반환 (반환값을 DB의 image_url 컬럼에 그대로 저장)
    String store(byte[] content, String contentType);

    // 저장된 이미지를 삭제한다 (구현체가 비동기로 처리할 수 있음)
    void delete(String url);

    // "image/jpeg; charset=..." 형태의 파라미터를 떼고 소문자로 정규화해 확장자를 구한다
    // 허용 목록에 없는 형식은 저장 거부 — 모든 어댑터가 이 한 곳의 규칙을 공유한다
    static String resolveExtension(String contentType) {
        String mimeType = contentType == null ? "" : contentType.split(";", 2)[0].trim();
        String extension = EXTENSIONS.get(mimeType.toLowerCase(Locale.ROOT));
        if (extension == null) {
            throw new CustomException(ErrorCode.COMMON_006);
        }
        return extension;
    }
}
