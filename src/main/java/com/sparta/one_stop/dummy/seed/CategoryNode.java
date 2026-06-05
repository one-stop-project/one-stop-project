package com.sparta.one_stop.dummy.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// categories.json 트리 노드 — name + 하위 children (잎은 children 없음 → 빈 리스트로 정규화)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoryNode(String name, List<CategoryNode> children) {
    public CategoryNode {
        children = (children == null) ? List.of() : children;
    }
}
