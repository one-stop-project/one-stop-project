package com.sparta.one_stop.global.storage;

// 이미지 저장 추상화 (포트)
// 1단계 구현 = LocalImageStorage(로컬 파일시스템), AWS 도입 후 = S3ImageStorage 어댑터 교체
// 입력을 바이트 + contentType으로만 받아 저장 매체에 종속되지 않는다
public interface ImageStorage {

    // 이미지 바이트를 저장하고 접근 가능한 URL을 반환 (반환값을 DB의 image_url 컬럼에 그대로 저장)
    String store(byte[] content, String contentType);

    // 저장된 이미지를 삭제한다 (구현체가 비동기로 처리할 수 있음)
    void delete(String url);
}
