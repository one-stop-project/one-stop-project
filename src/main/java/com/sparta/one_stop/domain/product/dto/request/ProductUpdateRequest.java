package com.sparta.one_stop.domain.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductUpdateRequest {

    // 부분 수정: null이면 해당 필드 변경 안 함(허용). 단 빈 문자열·공백만 값은 거부.
    // (?sU): 줄바꿈 포함 매칭 + 유니코드 공백(NBSP·전각공백 등)도 공백으로 인식 — \S 하나라도 있어야 통과, null은 @Pattern이 검증 건너뜀
    @Pattern(regexp = "(?sU).*\\S.*", message = "상품명은 공백일 수 없습니다")
    @Size(max = 200, message = "상품명은 200자 이하여야 합니다")
    private String name;

    @Pattern(regexp = "(?sU).*\\S.*", message = "상품 설명은 공백일 수 없습니다")
    private String description;

    @Size(max = 500, message = "썸네일 URL은 500자 이하여야 합니다")
    private String thumbnailUrl;

    @Size(min = 1, max = 3, message = "카테고리는 1~3개까지 선택 가능합니다")
    private List<Long> categoryIds;

    // null이면 태그 변경 없음, 빈 리스트면 태그 전체 삭제
    @Size(max = 10, message = "태그는 최대 10개까지 가능합니다")
    private List<@NotBlank(message = "태그는 비어있을 수 없습니다") @Size(max = 30, message = "태그는 30자 이하여야 합니다") String> tags;
}
