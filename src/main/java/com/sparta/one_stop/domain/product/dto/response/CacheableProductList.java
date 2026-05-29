package com.sparta.one_stop.domain.product.dto.response;

import java.io.Serializable;
import java.util.List;

// 상품 목록 캐싱용 wrapper — Spring Data Page는 Jackson 역직렬화 불가능해 직접 직렬화 가능한 record 사용
// 컨트롤러에서 PageImpl로 다시 감싸 응답
public record CacheableProductList(
    List<ProductSummaryResponse> content,
    int page,
    int size,
    long total
) implements Serializable {
}
