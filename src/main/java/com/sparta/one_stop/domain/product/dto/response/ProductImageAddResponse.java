package com.sparta.one_stop.domain.product.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductImageAddResponse {

    private int addedImageCount;
    private int totalImageCount;
    private String thumbnailUrl;
    // 방금 추가된 이미지들의 imageId를 함께 반환해 재조회 없이 개별 삭제·대표지정 호출이 가능하게 한다.
    private List<ProductImageResponse> addedImages;
}
