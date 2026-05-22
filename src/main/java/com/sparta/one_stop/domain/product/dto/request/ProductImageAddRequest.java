package com.sparta.one_stop.domain.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

// 상품 이미지 추가 요청 DTO
// 정책: 한 번에 1~10장, 10장 초과 불가
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImageAddRequest {

    @NotEmpty(message = "추가할 이미지는 최소 1장 이상 필요합니다")
    @Size(min = 1, max = 10, message = "이미지는 한 번에 1~10장까지 추가 가능합니다")
    private List<@NotBlank(message = "이미지 URL은 비어있을 수 없습니다") String> imageUrls;
}
