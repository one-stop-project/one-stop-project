package com.sparta.one_stop.domain.product.dto.response;

import java.util.List;

// 카테고리 트리 노드 (children 재귀)
// 같은 구조의 다른 데이터가 들어감
public record CategoryTreeResponse(
        Long id,
        String name,
        List<CategoryTreeResponse> children
) {
}
