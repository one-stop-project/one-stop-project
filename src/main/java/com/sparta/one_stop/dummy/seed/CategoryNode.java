package com.sparta.one_stop.dummy.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// categories.json 트리 노드 — name + 하위 children (잎은 children 없음 → 빈 리스트로 정규화)
// searchKeyword: 잎 카테고리의 네이버 검색어 보정값(선택). 이름 그대로는 결과가 적거나(특수문자)
//               맥락이 빠져(도서·반려동물) 엉뚱한 상품이 나오는 잎에만 지정. 미지정이면 이름에서 파생.
@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoryNode(String name, String searchKeyword, List<CategoryNode> children) {
    public CategoryNode {
        children = (children == null) ? List.of() : children;
    }
}
